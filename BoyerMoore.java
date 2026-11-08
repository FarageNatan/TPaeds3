import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//  CASAMENTO DE PADRAO - BOYER-MOORE
//
//  Ideias principais:
//    1) O padrao e comparado com o texto da DIREITA para a ESQUERDA.
//    2) Quando ha uma diferenca, o padrao pode "saltar" varias posicoes de uma
//       vez, usando duas heuristicas (vale o MAIOR dos dois saltos):
//
//       - CARACTERE RUIM: se o caractere do texto que causou a diferenca
//         aparece no padrao, alinha-se com a ultima ocorrencia dele no padrao;
//         se nao aparece, o padrao pula inteiro para depois dele.
//
//       - SUFIXO BOM: se um sufixo do padrao ja casou com o texto, desloca-se
//         o padrao ate que outra ocorrencia desse sufixo (ou um prefixo do
//         padrao que seja igual a parte do sufixo) se alinhe com o texto.
//
//  Quanto maior o padrao e o alfabeto (textos em linguagem natural, como as
//  sinopses), mais o algoritmo salta: na media faz MENOS que n comparacoes
//  (sublinear). No pior caso e O(n*m) para a versao basica, mas com a regra do
//  sufixo bom completa isso e raro em texto comum.

public class BoyerMoore implements BuscadorPadrao {

    private final char[] padrao;
    private final int[] caractereRuim;  // caractereRuim[c] = deslocamento sugerido pelo caractere c
    private final int[] sufixoBom;      // sufixoBom[i] = deslocamento se a diferenca ocorreu na posicao i
    private long comparacoes = 0;

    public BoyerMoore(String padrao) {
        this.padrao = padrao.toCharArray();
        this.caractereRuim = montarCaractereRuim(this.padrao);
        this.sufixoBom = montarSufixoBom(this.padrao);
    }

    // Tabela do caractere ruim. Para o caractere c, guarda quantas posicoes
    // o padrao pode andar quando c (no texto) causa a diferenca:
    //   - c nao esta no padrao  -> m (pula o padrao inteiro);
    //   - c esta no padrao      -> m - 1 - (ultima posicao de c no padrao,
    //                              ignorando a ultima letra do padrao).
    // O vetor tem 65536 posicoes (todos os valores possiveis de um char).
    private static int[] montarCaractereRuim(char[] p) {
        int m = p.length;
        int[] tabela = new int[Character.MAX_VALUE + 1];
        Arrays.fill(tabela, m);
        for (int i = 0; i < m - 1; i++) {
            tabela[p[i]] = m - 1 - i;
        }
        return tabela;
    }

    // Auxiliar do sufixo bom: suf[i] = tamanho do MAIOR sufixo do padrao que
    // termina na posicao i e tambem e sufixo do padrao inteiro.
    // Calculado em O(m) reaproveitando a janela [g, f] ja conhecida.
    private static int[] calcularSufixos(char[] p) {
        int m = p.length;
        int[] suf = new int[m];
        suf[m - 1] = m;
        int g = m - 1;
        int f = 0;
        for (int i = m - 2; i >= 0; i--) {
            if (i > g && suf[i + m - 1 - f] < i - g) {
                suf[i] = suf[i + m - 1 - f]; // reaproveita um valor ja calculado
            } else {
                if (i < g) g = i;
                f = i;
                while (g >= 0 && p[g] == p[g + m - 1 - f]) {
                    g--;
                }
                suf[i] = f - g;
            }
        }
        return suf;
    }

    // Tabela do sufixo bom (regra forte). sufixoBom[i] diz quanto deslocar
    // quando o padrao casou da posicao i+1 ate o fim e falhou em i.
    private static int[] montarSufixoBom(char[] p) {
        int m = p.length;
        int[] suf = calcularSufixos(p);
        int[] tabela = new int[m];

        // caso 3 (padrao): nenhuma ocorrencia do sufixo -> desloca m
        Arrays.fill(tabela, m);

        // caso 2: algum PREFIXO do padrao e igual a um sufixo do trecho casado
        int j = 0;
        for (int i = m - 1; i >= 0; i--) {
            if (suf[i] == i + 1) { // p[0..i] e tambem sufixo do padrao
                for (; j < m - 1 - i; j++) {
                    if (tabela[j] == m) {
                        tabela[j] = m - 1 - i;
                    }
                }
            }
        }

        // caso 1: o sufixo casado reaparece mais a esquerda no padrao
        for (int i = 0; i <= m - 2; i++) {
            tabela[m - 1 - suf[i]] = m - 1 - i;
        }
        return tabela;
    }

    @Override
    public String nome() {
        return "Boyer-Moore";
    }

    @Override
    public List<Integer> buscarTodos(String texto) {
        List<Integer> posicoes = new ArrayList<>();
        int m = padrao.length;
        int n = texto.length();
        if (m == 0) return posicoes;

        int s = 0; // posicao do texto onde o inicio do padrao esta alinhado
        while (s <= n - m) {
            // compara da direita para a esquerda
            int i = m - 1;
            while (i >= 0) {
                comparacoes++;
                if (padrao[i] != texto.charAt(s + i)) break;
                i--;
            }

            if (i < 0) {
                // casou o padrao inteiro
                posicoes.add(s);
                s += sufixoBom[0]; // desloca o padrao para a proxima ocorrencia possivel
            } else {
                // diferenca na posicao i: usa o MAIOR entre os dois saltos
                int saltoRuim = caractereRuim[texto.charAt(s + i)] - (m - 1 - i);
                s += Math.max(sufixoBom[i], saltoRuim);
            }
        }
        return posicoes;
    }

    @Override
    public long getComparacoes() {
        return comparacoes;
    }
}
