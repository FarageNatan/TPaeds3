import java.util.ArrayList;
import java.util.List;

//  CASAMENTO DE PADRAO - KNUTH-MORRIS-PRATT (KMP)
//
//  Problema da forca bruta: ao encontrar uma diferenca, volta o texto para
//  tras e recomeca a comparar, re-lendo caracteres que ja tinha visto.
//
//  Ideia do KMP: o texto NUNCA volta. Quando ha uma diferenca depois de ter
//  casado "j" caracteres do padrao, esses "j" caracteres sao conhecidos, e o
//  padrao pode ser deslocado de acordo com o maior BORDA (prefixo do padrao
//  que tambem e sufixo do trecho ja casado). Essas bordas sao pre-calculadas
//  na TABELA DE FALHAS (tambem chamada de "prefix function"):
//
//     falha[i] = tamanho do maior prefixo proprio de padrao[0..i] que tambem
//                e sufixo de padrao[0..i]
//
//  Exemplo: padrao "abab" -> falha = [0, 0, 1, 2]
//
//  Complexidade: O(m) para montar a tabela + O(n) para varrer o texto, no
//  PIOR caso (nao depende do conteudo). Boa escolha quando o texto pode ter
//  muita repeticao ou quando se quer garantia de tempo.

public class KMP implements BuscadorPadrao {

    private final char[] padrao;
    private final int[] falha;     // tabela de falhas
    private long comparacoes = 0;

    public KMP(String padrao) {
        this.padrao = padrao.toCharArray();
        this.falha = montarTabelaFalhas(this.padrao);
    }

    // Pre-processamento: calcula a tabela de falhas em O(m).
    // "k" e o tamanho da borda atual; quando o proximo caractere nao estende
    // a borda, encurtamos k usando a propria tabela (falha[k-1]).
    private static int[] montarTabelaFalhas(char[] p) {
        int[] f = new int[p.length];
        int k = 0;
        for (int i = 1; i < p.length; i++) {
            while (k > 0 && p[i] != p[k]) {
                k = f[k - 1];
            }
            if (p[i] == p[k]) {
                k++;
            }
            f[i] = k;
        }
        return f;
    }

    @Override
    public String nome() {
        return "KMP";
    }

    @Override
    public List<Integer> buscarTodos(String texto) {
        List<Integer> posicoes = new ArrayList<>();
        int m = padrao.length;
        if (m == 0) return posicoes;

        int j = 0; // quantos caracteres do padrao estao casados no momento
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);

            // enquanto houver diferenca, "recua" o padrao pela tabela de falhas
            // (o indice i do texto NAO recua)
            while (j > 0) {
                comparacoes++;
                if (c == padrao[j]) break;
                j = falha[j - 1];
            }

            // compara o caractere atual com o padrao (caso j == 0, ou apos o recuo)
            if (j == 0) {
                comparacoes++;
            }
            if (c == padrao[j]) {
                j++;
            }

            // padrao inteiro casado: registra a posicao inicial e continua
            // aproveitando a borda, para achar ocorrencias sobrepostas
            if (j == m) {
                posicoes.add(i - m + 1);
                j = falha[j - 1];
            }
        }
        return posicoes;
    }

    @Override
    public long getComparacoes() {
        return comparacoes;
    }
}
