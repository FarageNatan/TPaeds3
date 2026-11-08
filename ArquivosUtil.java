import java.io.BufferedInputStream;
//import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
//import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//  Funcoes de apoio para a Compressao de Dados (Etapa 3):
//    - leitura de arquivo inteiro para um vetor de bytes;
//    - comparacao de arquivos;
//    - controle das VERSOES de compressao ("nomeArquivo" + "nomeAlgoritmo" + "Compressao" + X);
//    - EscritorBits / LeitorBits: gravacao e leitura de bits individuais, usados
//      pelo Huffman (codigos de tamanho variavel) e pelo LZW (codigos de 9 a 16 bits).

public class ArquivosUtil {

    // Le o arquivo inteiro para memoria
    public static byte[] lerTudo(String caminho) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(caminho))) {
            return in.readAllBytes();
        }
    }

    // Compara o conteudo de dois arquivos byte a byte (usado para conferir
    // que a descompressao devolveu exatamente o arquivo original)
    public static boolean iguais(String a, String b) throws IOException {
        File fa = new File(a), fb = new File(b);
        if (fa.length() != fb.length()) return false;
        try (InputStream ia = new BufferedInputStream(new FileInputStream(fa));
             InputStream ib = new BufferedInputStream(new FileInputStream(fb))) {
            int x;
            while ((x = ia.read()) != -1) {
                if (x != ib.read()) return false;
            }
        }
        return true;
    }

    //  VERSOES DE COMPRESSAO
    //  Nome gerado: <nomeArquivo><nomeAlgoritmo>Compressao<X>
    //  ex.: filmesHuffmanCompressao1 e filmesLZWCompressao1

    public static String nomeComprimido(String nomeArquivo, String algoritmo, int versao) {
        return nomeArquivo + algoritmo + "Compressao" + versao;
    }

    // Procura, no diretorio atual, os arquivos de compressao ja criados e
    // devolve as versoes existentes (sem repeticao, em ordem crescente).
    public static List<Integer> versoesExistentes(String nomeArquivo) {
        Pattern p = Pattern.compile(Pattern.quote(nomeArquivo) + "(Huffman|LZW)Compressao(\\d+)");
        List<Integer> versoes = new ArrayList<>();
        File[] arquivos = new File(".").listFiles();
        if (arquivos == null) return versoes;

        for (File f : arquivos) {
            Matcher m = p.matcher(f.getName());
            if (m.matches()) {
                int v = Integer.parseInt(m.group(2));
                if (!versoes.contains(v)) versoes.add(v);
            }
        }
        versoes.sort(null);
        return versoes;
    }

    // Proxima versao = maior versao existente + 1 (as duas compressoes de uma
    // mesma execucao recebem o mesmo numero X)
    public static int proximaVersao(String nomeArquivo) {
        List<Integer> v = versoesExistentes(nomeArquivo);
        return v.isEmpty() ? 1 : v.get(v.size() - 1) + 1;
    }

    //  ESCRITA DE BITS
    //  Acumula bits num buffer de 8 e despeja cada byte completo no OutputStream.
    //  Os bits sao gravados do MAIS significativo para o menos significativo.

    public static class EscritorBits {
        private final OutputStream out;
        private int buffer = 0;     // bits ainda nao gravados
        private int qtdBuffer = 0;  // quantos bits o buffer tem (0..7)

        public EscritorBits(OutputStream out) { this.out = out; }

        // Grava os "n" bits menos significativos de "valor" (do mais para o menos significativo)
        public void escrever(long valor, int n) throws IOException {
            for (int i = n - 1; i >= 0; i--) {
                int bit = (int) ((valor >> i) & 1L);
                buffer = (buffer << 1) | bit;
                qtdBuffer++;
                if (qtdBuffer == 8) {
                    out.write(buffer);
                    buffer = 0;
                    qtdBuffer = 0;
                }
            }
        }

        // Completa o ultimo byte com zeros (a direita) e o grava
        public void finalizar() throws IOException {
            if (qtdBuffer > 0) {
                out.write(buffer << (8 - qtdBuffer));
                buffer = 0;
                qtdBuffer = 0;
            }
            out.flush();
        }
    }

    //  LEITURA DE BITS (operacao inversa do EscritorBits)

    public static class LeitorBits {
        private final InputStream in;
        private int buffer = 0;
        private int qtdBuffer = 0; // bits ainda nao consumidos do byte atual

        public LeitorBits(InputStream in) { this.in = in; }

        // Le um bit; lanca IOException se o arquivo acabar
        public int lerBit() throws IOException {
            if (qtdBuffer == 0) {
                buffer = in.read();
                if (buffer == -1) throw new IOException("Fim inesperado do arquivo comprimido.");
                qtdBuffer = 8;
            }
            qtdBuffer--;
            return (buffer >> qtdBuffer) & 1;
        }

        // Le "n" bits e os monta num inteiro (primeiro bit lido = mais significativo)
        public int lerBits(int n) throws IOException {
            int valor = 0;
            for (int i = 0; i < n; i++) {
                valor = (valor << 1) | lerBit();
            }
            return valor;
        }
    }
}
