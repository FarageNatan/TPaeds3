import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

//  ARVORE B+  (indice primario: id -> posicao do registro no arquivo de dados)
//
//  Por que B+ (e nao B ou B*):
//    - todos os pares (id, posicao) ficam nas FOLHAS; os nos internos guardam
//      apenas chaves-guia. A busca sempre termina numa folha e a remocao nunca
//      precisa trocar a chave por sucessora/antecessora dentro de nos internos;
//    - as folhas sao encadeadas (ponteiro "proxima"), permitindo percorrer
//      os ids em ordem crescente sem voltar a raiz;
//    - a B* so melhora a ocupacao das paginas (divisao 2 -> 3), ao custo de
//      uma insercao bem mais complexa - ganho pequeno para esta base.
//
//  Ordem (m) = numero maximo de FILHOS por pagina -> ate m-1 chaves por pagina.
//
//  Layout do arquivo:
//    cabecalho: [ordem:int][raiz:long]
//    pagina   : [n:int][folha:boolean][chaves:(m-1) int][posicoes:(m-1) long]
//               [filhos:m long][proxima:long]
//  Todas as paginas tem o mesmo tamanho, entao o endereco de uma pagina e
//  simplesmente o byte onde ela comeca no arquivo.

public class ArvoreBMais {
    private static final int POS_RAIZ = 4;      // offset da raiz no cabecalho
    private static final int TAM_CABECALHO = 12;

    private final RandomAccessFile arq;
    private final int ordem;
    private final int maxChaves;  // m - 1
    private final int minChaves;  // (m - 1) / 2 : abaixo disso a pagina precisa ser corrigida
    private final int tamPagina;
    private long raiz;            // -1 = arvore vazia

    // Pagina em memoria. Os vetores tem 1 posicao a mais que o permitido no
    // disco, para caber a chave extra antes da divisao (overflow temporario).
    private class Pagina {
        long endereco = -1;       // -1 = pagina nova, ainda nao gravada
        int n = 0;
        boolean folha = true;
        int[] chaves = new int[ordem];
        long[] posicoes = new long[ordem];
        long[] filhos = new long[ordem + 1];
        long proxima = -1;        // proxima folha (so usado nas folhas)
    }

    // Resultado de uma divisao: chave que sobe para o pai e a nova pagina da direita
    private static class Promocao {
        int chave;
        long direita;
    }

    // Abre o indice; se o arquivo ja existir, a ordem gravada nele prevalece sobre a informada
    public ArvoreBMais(String nomeArq, int ordemDesejada) throws IOException {
        boolean existe = new File(nomeArq).exists();
        arq = new RandomAccessFile(nomeArq, "rw");

        if (existe && arq.length() >= TAM_CABECALHO) {
            arq.seek(0);
            ordem = arq.readInt();
            raiz = arq.readLong();
        } else {
            if (ordemDesejada < 3) throw new IllegalArgumentException("a ordem da arvore deve ser no minimo 3");
            ordem = ordemDesejada;
            raiz = -1;
            arq.setLength(0);
            arq.writeInt(ordem);
            arq.writeLong(raiz);
        }

        maxChaves = ordem - 1;
        minChaves = (ordem - 1) / 2;
        tamPagina = 4 + 1 + 4 * maxChaves + 8 * maxChaves + 8 * ordem + 8;
    }

    public int getOrdem() {
        return ordem;
    }


    //  BUSCA

    // Devolve a posicao do registro no arquivo de dados, ou -1 se o id nao existir
    public long buscar(int id) throws IOException {
        Pagina folha = descerAteFolha(id);
        if (folha == null) return -1;
        int i = indiceNaFolha(folha, id);
        return (i >= 0) ? folha.posicoes[i] : -1;
    }

    // Troca a posicao associada a um id (usado quando o registro muda de lugar no arquivo)
    public boolean atualizar(int id, long novaPosicao) throws IOException {
        Pagina folha = descerAteFolha(id);
        if (folha == null) return false;
        int i = indiceNaFolha(folha, id);
        if (i < 0) return false;
        folha.posicoes[i] = novaPosicao;
        escreverPagina(folha);
        return true;
    }

    // Desce da raiz ate a folha onde o id esta (ou estaria)
    private Pagina descerAteFolha(int id) throws IOException {
        if (raiz == -1) return null;
        Pagina p = lerPagina(raiz);
        while (!p.folha) {
            p = lerPagina(p.filhos[indiceFilho(p, id)]);
        }
        return p;
    }

    // Indice do filho a seguir: quantidade de chaves <= id (chave igual fica a direita)
    private int indiceFilho(Pagina p, int id) {
        int i = 0;
        while (i < p.n && id >= p.chaves[i]) i++;
        return i;
    }

    // Posicao do id dentro de uma folha, ou -1
    private int indiceNaFolha(Pagina folha, int id) {
        for (int i = 0; i < folha.n; i++) {
            if (folha.chaves[i] == id) return i;
        }
        return -1;
    }


    //  INSERCAO

    // Insere o par (id, posicao); devolve false se o id ja estiver no indice
    public boolean inserir(int id, long posicao) throws IOException {
        if (raiz == -1) {
            Pagina r = new Pagina();
            r.chaves[0] = id;
            r.posicoes[0] = posicao;
            r.n = 1;
            escreverPagina(r);
            gravarRaiz(r.endereco);
            return true;
        }
        if (buscar(id) != -1) return false;

        Promocao pr = inserir(raiz, id, posicao);
        if (pr != null) {
            // a raiz foi dividida: cria uma nova raiz e a arvore cresce 1 nivel
            Pagina r = new Pagina();
            r.folha = false;
            r.n = 1;
            r.chaves[0] = pr.chave;
            r.filhos[0] = raiz;
            r.filhos[1] = pr.direita;
            escreverPagina(r);
            gravarRaiz(r.endereco);
        }
        return true;
    }

    // Insercao recursiva; devolve a promocao se a pagina precisou ser dividida
    private Promocao inserir(long endereco, int id, long posicao) throws IOException {
        Pagina p = lerPagina(endereco);

        if (p.folha) {
            // desloca as chaves maiores e encaixa o id na posicao ordenada
            int i = p.n;
            while (i > 0 && p.chaves[i - 1] > id) {
                p.chaves[i] = p.chaves[i - 1];
                p.posicoes[i] = p.posicoes[i - 1];
                i--;
            }
            p.chaves[i] = id;
            p.posicoes[i] = posicao;
            p.n++;
        } else {
            int i = indiceFilho(p, id);
            Promocao pr = inserir(p.filhos[i], id, posicao);
            if (pr == null) return null;

            // o filho dividiu: encaixa a chave promovida em i e o novo filho em i+1
            for (int j = p.n; j > i; j--) {
                p.chaves[j] = p.chaves[j - 1];
                p.filhos[j + 1] = p.filhos[j];
            }
            p.chaves[i] = pr.chave;
            p.filhos[i + 1] = pr.direita;
            p.n++;
        }

        if (p.n <= maxChaves) {
            escreverPagina(p);
            return null;
        }
        return dividir(p);
    }

    // Divide uma pagina cheia em duas e devolve a chave que deve subir para o pai
    private Promocao dividir(Pagina p) throws IOException {
        Pagina nova = new Pagina();
        nova.folha = p.folha;
        int meio = p.n / 2;
        Promocao pr = new Promocao();

        if (p.folha) {
            // folha: metade direita vai para a nova pagina; a 1a chave dela e COPIADA para o pai
            nova.n = p.n - meio;
            System.arraycopy(p.chaves, meio, nova.chaves, 0, nova.n);
            System.arraycopy(p.posicoes, meio, nova.posicoes, 0, nova.n);
            p.n = meio;

            nova.proxima = p.proxima;  // mantem o encadeamento das folhas
            escreverPagina(nova);
            p.proxima = nova.endereco;
            pr.chave = nova.chaves[0];
        } else {
            // no interno: a chave do meio SOBE e nao fica em nenhuma das duas paginas
            pr.chave = p.chaves[meio];
            nova.n = p.n - meio - 1;
            System.arraycopy(p.chaves, meio + 1, nova.chaves, 0, nova.n);
            System.arraycopy(p.filhos, meio + 1, nova.filhos, 0, nova.n + 1);
            p.n = meio;
            escreverPagina(nova);
        }

        escreverPagina(p);
        pr.direita = nova.endereco;
        return pr;
    }


    //  REMOCAO

    // Remove o id do indice; devolve false se ele nao existir
    public boolean remover(int id) throws IOException {
        if (buscar(id) == -1) return false;

        remover(raiz, id);

        // a raiz pode ficar com menos que o minimo; so e tratada se esvaziar
        Pagina r = lerPagina(raiz);
        if (r.n == 0) {
            gravarRaiz(r.folha ? -1 : r.filhos[0]); // arvore vazia ou 1 nivel a menos
        }
        return true;
    }

    // Remocao recursiva; devolve true se a pagina ficou abaixo do minimo de chaves
    private boolean remover(long endereco, int id) throws IOException {
        Pagina p = lerPagina(endereco);

        if (p.folha) {
            int i = indiceNaFolha(p, id);
            for (int j = i; j < p.n - 1; j++) {
                p.chaves[j] = p.chaves[j + 1];
                p.posicoes[j] = p.posicoes[j + 1];
            }
            p.n--;
            escreverPagina(p);
        } else {
            int i = indiceFilho(p, id);
            if (!remover(p.filhos[i], id)) return false;
            corrigirFilho(p, i);
            escreverPagina(p);
        }
        return p.n < minChaves;
    }

    // Corrige o filho i do pai que ficou abaixo do minimo:
    // tenta emprestar de um irmao; se nenhum puder emprestar, funde com um deles
    private void corrigirFilho(Pagina pai, int i) throws IOException {
        Pagina filho = lerPagina(pai.filhos[i]);
        Pagina esq = (i > 0) ? lerPagina(pai.filhos[i - 1]) : null;
        Pagina dir = (i < pai.n) ? lerPagina(pai.filhos[i + 1]) : null;

        if (esq != null && esq.n > minChaves) {
            emprestarDaEsquerda(pai, i, esq, filho);
        } else if (dir != null && dir.n > minChaves) {
            emprestarDaDireita(pai, i, filho, dir);
        } else if (esq != null) {
            fundir(pai, i - 1, esq, filho);
        } else {
            fundir(pai, i, filho, dir);
        }
    }

    // Passa a ultima chave do irmao esquerdo para o inicio do filho
    private void emprestarDaEsquerda(Pagina pai, int i, Pagina esq, Pagina filho) throws IOException {
        // abre espaco na frente do filho
        for (int j = filho.n; j > 0; j--) {
            filho.chaves[j] = filho.chaves[j - 1];
            filho.posicoes[j] = filho.posicoes[j - 1];
        }
        if (!filho.folha) {
            for (int j = filho.n + 1; j > 0; j--) filho.filhos[j] = filho.filhos[j - 1];
        }

        if (filho.folha) {
            filho.chaves[0] = esq.chaves[esq.n - 1];
            filho.posicoes[0] = esq.posicoes[esq.n - 1];
            pai.chaves[i - 1] = filho.chaves[0];        // nova chave-guia
        } else {
            filho.chaves[0] = pai.chaves[i - 1];        // chave do pai desce
            filho.filhos[0] = esq.filhos[esq.n];
            pai.chaves[i - 1] = esq.chaves[esq.n - 1];  // chave do irmao sobe
        }
        filho.n++;
        esq.n--;

        escreverPagina(esq);
        escreverPagina(filho);
    }

    // Passa a primeira chave do irmao direito para o fim do filho
    private void emprestarDaDireita(Pagina pai, int i, Pagina filho, Pagina dir) throws IOException {
        if (filho.folha) {
            filho.chaves[filho.n] = dir.chaves[0];
            filho.posicoes[filho.n] = dir.posicoes[0];
        } else {
            filho.chaves[filho.n] = pai.chaves[i];      // chave do pai desce
            filho.filhos[filho.n + 1] = dir.filhos[0];
            pai.chaves[i] = dir.chaves[0];              // chave do irmao sobe
        }
        filho.n++;

        // retira o primeiro elemento do irmao direito
        for (int j = 0; j < dir.n - 1; j++) {
            dir.chaves[j] = dir.chaves[j + 1];
            dir.posicoes[j] = dir.posicoes[j + 1];
        }
        if (!dir.folha) {
            for (int j = 0; j < dir.n; j++) dir.filhos[j] = dir.filhos[j + 1];
        }
        dir.n--;

        if (filho.folha) pai.chaves[i] = dir.chaves[0]; // nova chave-guia

        escreverPagina(filho);
        escreverPagina(dir);
    }

    // Junta "dir" dentro de "esq" e retira do pai a chave k e o filho k+1.
    // A pagina "dir" fica abandonada no arquivo (espaco nao reaproveitado).
    private void fundir(Pagina pai, int k, Pagina esq, Pagina dir) throws IOException {
        if (esq.folha) {
            System.arraycopy(dir.chaves, 0, esq.chaves, esq.n, dir.n);
            System.arraycopy(dir.posicoes, 0, esq.posicoes, esq.n, dir.n);
            esq.n += dir.n;
            esq.proxima = dir.proxima;
        } else {
            esq.chaves[esq.n] = pai.chaves[k];          // chave separadora desce
            System.arraycopy(dir.chaves, 0, esq.chaves, esq.n + 1, dir.n);
            System.arraycopy(dir.filhos, 0, esq.filhos, esq.n + 1, dir.n + 1);
            esq.n += dir.n + 1;
        }
        escreverPagina(esq);

        for (int j = k; j < pai.n - 1; j++) pai.chaves[j] = pai.chaves[j + 1];
        for (int j = k + 1; j < pai.n; j++) pai.filhos[j] = pai.filhos[j + 1];
        pai.n--;
    }


    //  ACESSO AO ARQUIVO

    // Le uma pagina inteira de uma vez e converte os bytes nos campos
    private Pagina lerPagina(long endereco) throws IOException {
        byte[] ba = new byte[tamPagina];
        arq.seek(endereco);
        arq.readFully(ba);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(ba));

        Pagina p = new Pagina();
        p.endereco = endereco;
        p.n = dis.readInt();
        p.folha = dis.readBoolean();
        for (int i = 0; i < maxChaves; i++) p.chaves[i] = dis.readInt();
        for (int i = 0; i < maxChaves; i++) p.posicoes[i] = dis.readLong();
        for (int i = 0; i < ordem; i++) p.filhos[i] = dis.readLong();
        p.proxima = dis.readLong();
        return p;
    }

    // Grava a pagina no seu endereco; pagina nova vai para o fim do arquivo
    private void escreverPagina(Pagina p) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(tamPagina);
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(p.n);
        dos.writeBoolean(p.folha);
        for (int i = 0; i < maxChaves; i++) dos.writeInt(p.chaves[i]);
        for (int i = 0; i < maxChaves; i++) dos.writeLong(p.posicoes[i]);
        for (int i = 0; i < ordem; i++) dos.writeLong(p.filhos[i]);
        dos.writeLong(p.proxima);

        if (p.endereco == -1) p.endereco = arq.length();
        arq.seek(p.endereco);
        arq.write(baos.toByteArray());
    }

    // Atualiza a raiz em memoria e no cabecalho
    private void gravarRaiz(long novaRaiz) throws IOException {
        raiz = novaRaiz;
        arq.seek(POS_RAIZ);
        arq.writeLong(raiz);
    }

    public void fechar() throws IOException {
        arq.close();
    }
}
