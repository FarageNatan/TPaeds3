import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.PriorityQueue;

//  COMPRESSAO DE DADOS - CODIFICACAO DE HUFFMAN
//
//  Ideia: simbolos (aqui, BYTES do arquivo) que aparecem muito recebem codigos
//  curtos; simbolos raros recebem codigos longos. Os codigos sao "livres de
//  prefixo" (nenhum codigo e comeco de outro), entao o fluxo de bits pode ser
//  decodificado sem separadores.
//
//  Por que bytes (e nao campos/strings)? O enunciado exige comprimir TODO o
//  arquivo - cabecalho, lapides, indicadores de tamanho, floats, textos. Tratando
//  o arquivo como uma sequencia de bytes, tudo passa pela mesma compressao.
//
//  Passos da compressao:
//    1) conta a frequencia de cada byte (0..255);
//    2) monta a arvore de Huffman com uma fila de prioridades (junta sempre os
//       dois nos de MENOR frequencia ate sobrar a raiz);
//    3) percorre a arvore gerando o codigo (sequencia de bits) de cada byte;
//    4) grava: [tamanho original][tabela de frequencias][bits codificados].
//
//  Passos da descompressao:
//    1) le a tabela de frequencias e reconstroi A MESMA arvore (a construcao e
//       deterministica, inclusive nos desempates);
//    2) le bit a bit descendo na arvore (0 = esquerda, 1 = direita); ao chegar
//       numa folha, emite o byte dela e volta para a raiz.
//
//  Formato do arquivo comprimido:
//    [tamanhoOriginal: long]
//    [qtdSimbolosDistintos: int]
//    qtdSimbolosDistintos vezes: [byte: 1 byte][frequencia: int]
//    [bits codificados, empacotados em bytes; o ultimo byte e completado com 0]

public class Huffman {

    // No da arvore de Huffman
    private static class No {
        final int simbolo;   // 0..255 nas folhas; -1 nos nos internos
        final long freq;     // frequencia (soma dos filhos nos internos)
        final int ordem;     // numero de criacao: usado para desempatar de forma deterministica
        final No esq, dir;

        No(int simbolo, long freq, int ordem, No esq, No dir) {
            this.simbolo = simbolo;
            this.freq = freq;
            this.ordem = ordem;
            this.esq = esq;
            this.dir = dir;
        }

        boolean folha() { return esq == null; }
    }

    // Monta a arvore a partir das frequencias. Folhas sao criadas em ordem de
    // byte e os nos internos recebem ordens crescentes; o desempate (mesma
    // frequencia) pela ordem garante que compressor e descompressor montem
    // exatamente a mesma arvore.
    private static No montarArvore(long[] freq) {
        PriorityQueue<No> fila = new PriorityQueue<>((a, b) ->
                (a.freq != b.freq) ? Long.compare(a.freq, b.freq) : Integer.compare(a.ordem, b.ordem));

        int ordem = 0;
        for (int s = 0; s < 256; s++) {
            if (freq[s] > 0) {
                fila.add(new No(s, freq[s], ordem++, null, null));
            }
        }
        if (fila.isEmpty()) return null;

        // junta os dois menores ate restar um unico no (a raiz)
        while (fila.size() > 1) {
            No a = fila.poll();
            No b = fila.poll();
            fila.add(new No(-1, a.freq + b.freq, ordem++, a, b));
        }
        return fila.poll();
    }

    // Percorre a arvore (esquerda = bit 0, direita = bit 1) e preenche, para
    // cada byte, o codigo (guardado num long) e seu comprimento em bits.
    private static void gerarCodigos(No no, long codigo, int tamanho, long[] codigos, int[] tamanhos) {
        if (no.folha()) {
            codigos[no.simbolo] = codigo;
            tamanhos[no.simbolo] = tamanho;
            return;
        }
        gerarCodigos(no.esq, codigo << 1, tamanho + 1, codigos, tamanhos);
        gerarCodigos(no.dir, (codigo << 1) | 1, tamanho + 1, codigos, tamanhos);
    }

    //  COMPRESSAO

    public static void comprimir(String arquivoEntrada, String arquivoSaida) throws IOException {
        // O arquivo e lido por inteiro: a base tem poucos MB e isso simplifica
        // as duas passadas (contagem de frequencias e codificacao).
        byte[] dados = ArquivosUtil.lerTudo(arquivoEntrada);

        // 1) frequencia de cada byte
        long[] freq = new long[256];
        for (byte b : dados) {
            freq[b & 0xFF]++;
        }

        // 2) arvore e 3) codigos
        No raiz = montarArvore(freq);
        long[] codigos = new long[256];
        int[] tamanhos = new int[256];
        int distintos = 0;
        for (int s = 0; s < 256; s++) if (freq[s] > 0) distintos++;

        if (raiz != null) {
            if (raiz.folha()) {
                // so existe 1 byte distinto: a arvore e uma unica folha. Damos a ele o codigo "0" (1 bit).
                codigos[raiz.simbolo] = 0;
                tamanhos[raiz.simbolo] = 1;
            } else {
                gerarCodigos(raiz, 0, 0, codigos, tamanhos);
            }
        }

        // 4) grava cabecalho + tabela de frequencias + bits
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(arquivoSaida)))) {

            out.writeLong(dados.length);
            out.writeInt(distintos);
            for (int s = 0; s < 256; s++) {
                if (freq[s] > 0) {
                    out.writeByte(s);
                    out.writeInt((int) freq[s]); // base < 2GB, logo a frequencia cabe em int
                }
            }

            ArquivosUtil.EscritorBits bits = new ArquivosUtil.EscritorBits(out);
            for (byte b : dados) {
                int s = b & 0xFF;
                bits.escrever(codigos[s], tamanhos[s]);
            }
            bits.finalizar(); // completa o ultimo byte com zeros
        }
    }

    //  DESCOMPRESSAO

    public static void descomprimir(String arquivoEntrada, String arquivoSaida) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(arquivoEntrada)));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(arquivoSaida))) {

            long tamanhoOriginal = in.readLong();
            int distintos = in.readInt();

            // le a tabela de frequencias e reconstroi a arvore
            long[] freq = new long[256];
            int unico = -1;
            for (int i = 0; i < distintos; i++) {
                int s = in.readUnsignedByte();
                freq[s] = in.readInt() & 0xFFFFFFFFL;
                unico = s;
            }

            if (tamanhoOriginal == 0) return; // arquivo original vazio

            if (distintos == 1) {
                // caso especial: o arquivo so tinha um byte repetido; nao ha bits a ler
                for (long i = 0; i < tamanhoOriginal; i++) out.write(unico);
                return;
            }

            No raiz = montarArvore(freq);
            ArquivosUtil.LeitorBits bits = new ArquivosUtil.LeitorBits(in);

            // le bit a bit, descendo na arvore ate achar uma folha
            for (long i = 0; i < tamanhoOriginal; i++) {
                No atual = raiz;
                while (!atual.folha()) {
                    atual = (bits.lerBit() == 0) ? atual.esq : atual.dir;
                }
                out.write(atual.simbolo);
            }
        }
    }
}
