import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

//  COMPRESSAO DE DADOS - LZW (Lempel-Ziv-Welch)
//
//  Ideia: em vez de codificar simbolo a simbolo, o LZW vai construindo um
//  DICIONARIO de sequencias de bytes que ja apareceram e passa a emitir apenas
//  o numero (codigo) da sequencia. Quando um trecho se repete (nomes de
//  generos, paises, idiomas, palavras das sinopses...), ele vira um unico codigo.
//  O dicionario NAO e gravado: o descompressor o reconstroi sozinho, na mesma
//  ordem, a medida que le os codigos.
//
//  DECISOES DO GRUPO
//    - Dicionario inicial: os 256 bytes possiveis (codigos 0..255), assim
//      qualquer arquivo pode ser comprimido, inclusive cabecalho, lapides e
//      indicadores de tamanho. Alem deles, 2 codigos de controle:
//         256 = LIMPAR (reinicia o dicionario)
//         257 = FIM    (fim dos dados)
//      Os codigos novos comecam em 258.
//    - Largura dos codigos VARIAVEL, de 9 a 16 bits: comeca em 9 bits e
//      aumenta quando o dicionario passa de 512, 1024, ... entradas. Assim os
//      primeiros codigos nao desperdicam bits.
//    - Dicionario cheio (65536 entradas): emite LIMPAR e recomeca do dicionario
//      inicial, o que adapta o dicionario a mudancas no tipo de dado ao longo
//      do arquivo (ex.: de textos para numeros).
//
//  Formato do arquivo comprimido:
//    [tamanhoOriginal: long][codigos empacotados em bits ... FIM]

public class LZW {

    private static final int LIMPAR = 256;
    private static final int FIM = 257;
    private static final int PRIMEIRO_CODIGO = 258;
    private static final int LARGURA_INICIAL = 9;
    private static final int LARGURA_MAXIMA = 16;
    private static final int MAX_CODIGOS = 1 << LARGURA_MAXIMA;

    //  COMPRESSAO
    //  Mantem uma sequencia atual "w" (guardada como o CODIGO dela). Para cada
    //  byte b lido:
    //     - se "w + b" ja esta no dicionario, w passa a ser "w + b";
    //     - senao: emite o codigo de w, cadastra "w + b" com um codigo novo e
    //       recomeca com w = b.
    //  No dicionario da compressao, a chave de "w + b" e (codigo de w, b),
    //  empacotada num int: (codigoW << 8) | b.

    public static void comprimir(String arquivoEntrada, String arquivoSaida) throws IOException {
        byte[] dados = ArquivosUtil.lerTudo(arquivoEntrada);

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(arquivoSaida)))) {

            out.writeLong(dados.length);
            ArquivosUtil.EscritorBits bits = new ArquivosUtil.EscritorBits(out);

            HashMap<Integer, Integer> dicionario = new HashMap<>();
            int proximo = PRIMEIRO_CODIGO; // proximo codigo livre
            int largura = LARGURA_INICIAL; // bits usados por codigo neste momento
            int w = -1;                    // sequencia atual (codigo); -1 = vazia

            for (byte byteLido : dados) {
                int b = byteLido & 0xFF;

                if (w == -1) {
                    w = b; // sequencia de 1 byte: o codigo e o proprio byte
                    continue;
                }

                int chave = (w << 8) | b;
                Integer codigo = dicionario.get(chave);

                if (codigo != null) {
                    w = codigo; // "w + b" ja existe: estende a sequencia
                } else {
                    // Antes de emitir, garante que a largura comporta o maior codigo
                    // que o descompressor pode receber (proximo - 1).
                    while (proximo > (1 << largura)) largura++;
                    bits.escrever(w, largura);

                    dicionario.put(chave, proximo++); // cadastra "w + b"

                    // Dicionario cheio: avisa o descompressor e reinicia os dois lados
                    if (proximo == MAX_CODIGOS) {
                        while (proximo > (1 << largura)) largura++;
                        bits.escrever(LIMPAR, largura);
                        dicionario.clear();
                        proximo = PRIMEIRO_CODIGO;
                        largura = LARGURA_INICIAL;
                    }
                    w = b; // recomeca a sequencia a partir do byte que nao coube
                }
            }

            if (w != -1) {
                while (proximo > (1 << largura)) largura++;
                bits.escrever(w, largura); // descarrega a ultima sequencia
            }
            while (proximo > (1 << largura)) largura++;
            bits.escrever(FIM, largura);
            bits.finalizar();
        }
    }

    //  DESCOMPRESSAO
    //  Reconstroi o dicionario enquanto le. Cada entrada nova e "sequencia do
    //  codigo anterior + primeiro byte da sequencia atual"; por isso o
    //  descompressor cadastra com UM PASSO DE ATRASO em relacao ao compressor.
    //  Cada entrada e guardada como (prefixo, ultimo byte), formando uma lista
    //  encadeada que, percorrida de tras para frente, da a sequencia.
    //
    //  Caso especial: o codigo lido pode ser exatamente o proximo codigo livre
    //  (o compressor o criou e usou logo em seguida, e o descompressor ainda
    //  nao o cadastrou). Isso so acontece quando a sequencia e
    //  "anterior + primeiro byte do anterior" - e e assim que a resolvemos.

    public static void descomprimir(String arquivoEntrada, String arquivoSaida) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(arquivoEntrada)));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(arquivoSaida))) {

            long tamanhoOriginal = in.readLong();
            ArquivosUtil.LeitorBits bits = new ArquivosUtil.LeitorBits(in);

            int[] prefixo = new int[MAX_CODIGOS];  // codigo da sequencia sem o ultimo byte
            byte[] sufixo = new byte[MAX_CODIGOS]; // ultimo byte da sequencia
            byte[] primeiro = new byte[MAX_CODIGOS]; // primeiro byte da sequencia (evita percorrer a cadeia)
            for (int i = 0; i < 256; i++) {        // dicionario inicial: um byte por codigo
                sufixo[i] = (byte) i;
                primeiro[i] = (byte) i;
                prefixo[i] = -1;
            }

            byte[] pilha = new byte[MAX_CODIGOS];  // area para montar uma sequencia de tras para frente
            int proximo = PRIMEIRO_CODIGO;
            int largura = LARGURA_INICIAL;
            int anterior = -1;                     // codigo lido antes do atual
            long gravados = 0;

            while (true) {
                // O compressor ja tem uma entrada a mais que nos: o limite de
                // largura considera "proximo + 1".
                while (proximo + 1 > (1 << largura)) largura++;
                int codigo = bits.lerBits(largura);

                if (codigo == FIM) break;

                if (codigo == LIMPAR) {          // reinicia o dicionario
                    proximo = PRIMEIRO_CODIGO;
                    largura = LARGURA_INICIAL;
                    anterior = -1;
                    continue;
                }

                if (anterior == -1) {
                    // primeiro codigo apos o inicio/LIMPAR: e sempre um byte simples
                    out.write(codigo);
                    gravados++;
                    anterior = codigo;
                    continue;
                }

                if (codigo < proximo) {
                    // codigo conhecido: cadastra "anterior + primeiro byte da sequencia atual"
                    prefixo[proximo] = anterior;
                    sufixo[proximo] = primeiro[codigo];
                    primeiro[proximo] = primeiro[anterior];
                    proximo++;
                } else {
                    // caso especial: codigo == proximo; a sequencia e "anterior + primeiro byte do anterior"
                    prefixo[proximo] = anterior;
                    sufixo[proximo] = primeiro[anterior];
                    primeiro[proximo] = primeiro[anterior];
                    proximo++;
                }

                // monta a sequencia do codigo atual: sobe pela cadeia de prefixos
                // gravando na pilha de tras para frente, depois escreve na ordem certa
                int topo = 0;
                for (int c = codigo; c != -1; c = prefixo[c]) {
                    pilha[topo++] = sufixo[c];
                }
                for (int i = topo - 1; i >= 0; i--) {
                    out.write(pilha[i]);
                }
                gravados += topo;
                anterior = codigo;
            }

            if (gravados != tamanhoOriginal) {
                throw new IOException("Arquivo LZW corrompido: esperados " + tamanhoOriginal
                        + " bytes, obtidos " + gravados + ".");
            }
        }
    }
}
