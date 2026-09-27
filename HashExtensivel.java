import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

//  HASHING EXTENSIVEL  (indice secundario: id -> posicao do registro no arquivo de dados)
//
//  Campo indexado: o id do filme (exigido pelo enunciado). Faz sentido
//  reaproveitar essa chave aqui porque ela ja e a chave primaria unica dos
//  registros, ja usada pela Arvore B+ - o hashing extensivel funciona entao
//  como uma SEGUNDA forma de localizar um registro pelo id, O(1) em vez de
//  O(log n), e serve para comparar as duas abordagens no video (ambas devem
//  devolver a mesma posicao para o mesmo id).
//
//  Por que Hashing Extensivel (e nao hashing estatico):
//    - a base cresce (carga do CSV, criacoes via CRUD); com um numero fixo
//      de buckets a colisao so pioraria com o tempo. No extensivel, o
//      DIRETORIO dobra de tamanho sob demanda e so o bucket que de fato
//      lotou e dividido - os demais continuam intactos.
//
//  Funcao hash exigida pelo enunciado: h(k) = k mod 2^p, onde p e a
//  profundidade global do diretorio. Como 2^p e potencia de 2, h(k) e
//  simplesmente os p bits menos significativos de k (k & (2^p - 1)) - e
//  assim que o diretorio e indexado (ver indiceDiretorio).
//
//  Capacidade do bucket (X): definida como 5% do tamanho inicial da base,
//  conforme pedido no enunciado. O arquivo imdb_movies.csv tem 10.178
//  registros, logo X = 509 (ver CAPACIDADE_BUCKET_PADRAO).
//
//  Layout dos arquivos:
//    diretorio (<nome>.dir): [capacidadeBucket:int][profundidadeGlobal p:int]
//                             [ponteiros: 2^p long] (endereco do bucket no
//                             arquivo de buckets). Arquivo pequeno, mantido
//                             inteiro em memoria e reescrito a cada mudanca.
//    buckets   (<nome>.bck): sequencia de buckets de tamanho FIXO:
//                             [profundidadeLocal:int][n:int]
//                             [ids: capacidadeBucket int][posicoes: capacidadeBucket long]

public class HashExtensivel {

    // 5% dos 10.178 registros do imdb_movies.csv (tamanho inicial da base escolhida)
    public static final int CAPACIDADE_BUCKET_PADRAO = 509;

    private final RandomAccessFile dir;
    private final RandomAccessFile bck;
    private final int capacidade;      // X: quantos pares (id, posicao) cabem por bucket
    private final int tamBucketBytes;  // tamanho fixo, em bytes, de 1 bucket no arquivo
    private int profundidadeGlobal;    // p
    private long[] diretorio;          // 2^p enderecos de bucket, mantido em memoria

    // Bucket em memoria
    private static class Bucket {
        long endereco = -1; // -1 = bucket novo, ainda nao gravado
        int profundidadeLocal;
        int n = 0;
        int[] ids;
        long[] posicoes;

        Bucket(int capacidade) {
            ids = new int[capacidade];
            posicoes = new long[capacidade];
        }
    }

    // Abre (ou cria) o indice de hashing extensivel a partir do nome base
    // (gera os arquivos <nomeBase>.dir e <nomeBase>.bck)
    public HashExtensivel(String nomeBase, int capacidadeBucketPadrao) throws IOException {
        boolean existia = new File(nomeBase + ".dir").exists();
        dir = new RandomAccessFile(nomeBase + ".dir", "rw");
        bck = new RandomAccessFile(nomeBase + ".bck", "rw");

        if (existia && dir.length() > 0) {
            // carrega o estado ja existente do diretorio
            dir.seek(0);
            capacidade = dir.readInt();
            profundidadeGlobal = dir.readInt();
            diretorio = new long[1 << profundidadeGlobal];
            for (int i = 0; i < diretorio.length; i++) {
                diretorio[i] = dir.readLong();
            }
        } else {
            // indice novo: profundidade 0 (1 bucket so, que recebe tudo ate lotar)
            capacidade = capacidadeBucketPadrao;
            profundidadeGlobal = 0;
            diretorio = new long[1];

            Bucket b0 = new Bucket(capacidade);
            b0.profundidadeLocal = 0;
            escreverBucket(b0); // vai para o endereco 0 do arquivo de buckets
            diretorio[0] = b0.endereco;
            persistirDiretorio();
        }

        tamBucketBytes = 4 + 4 + 4 * capacidade + 8 * capacidade;
    }

    public int getProfundidadeGlobal() { return profundidadeGlobal; }
    public int getCapacidadeBucket() { return capacidade; }

    // Indice no diretorio = h(id) = id mod 2^p, aqui calculado como os p
    // bits menos significativos do id (equivalente e mais direto de aplicar)
    private int indiceDiretorio(int id) {
        if (profundidadeGlobal == 0) return 0;
        return id & ((1 << profundidadeGlobal) - 1);
    }


    //  BUSCA

    // Devolve a posicao do registro no arquivo de dados, ou -1 se o id nao existir
    public long buscar(int id) throws IOException {
        Bucket b = lerBucket(diretorio[indiceDiretorio(id)]);
        for (int i = 0; i < b.n; i++) {
            if (b.ids[i] == id) return b.posicoes[i];
        }
        return -1;
    }


    //  INSERCAO

    // Insere o par (id, posicao); devolve false se o id ja estiver no indice
    public boolean inserir(int id, long posicao) throws IOException {
        int idx = indiceDiretorio(id);
        Bucket b = lerBucket(diretorio[idx]);

        for (int i = 0; i < b.n; i++) {
            if (b.ids[i] == id) return false; // ja existe
        }

        if (b.n < capacidade) {
            b.ids[b.n] = id;
            b.posicoes[b.n] = posicao;
            b.n++;
            escreverBucket(b);
            return true;
        }

        // bucket cheio: divide e tenta de novo (agora ha espaco em algum dos dois)
        dividirBucket(idx, b);
        return inserir(id, posicao);
    }

    // Divide um bucket cheio em dois, redistribuindo seus pares pelo bit
    // extra do id; dobra o diretorio antes, se a profundidade local do
    // bucket ja tiver alcancado a profundidade global
    private void dividirBucket(int idx, Bucket cheio) throws IOException {
        if (cheio.profundidadeLocal == profundidadeGlobal) {
            dobrarDiretorio();
        }

        int antigaLocal = cheio.profundidadeLocal;
        int novaLocal = antigaLocal + 1;

        // guarda os pares atuais e zera o bucket antigo para redistribuir
        int[] idsAntigos = cheio.ids;
        long[] posAntigas = cheio.posicoes;
        int nAntigo = cheio.n;

        cheio.ids = new int[capacidade];
        cheio.posicoes = new long[capacidade];
        cheio.n = 0;
        cheio.profundidadeLocal = novaLocal;

        Bucket irmao = new Bucket(capacidade);
        irmao.profundidadeLocal = novaLocal;

        // o bit que decide o lado e exatamente o bit na posicao "antigaLocal"
        // do id: e o bit novo que passou a fazer parte do indice do diretorio
        for (int i = 0; i < nAntigo; i++) {
            int bit = (idsAntigos[i] >> antigaLocal) & 1;
            if (bit == 0) {
                cheio.ids[cheio.n] = idsAntigos[i];
                cheio.posicoes[cheio.n] = posAntigas[i];
                cheio.n++;
            } else {
                irmao.ids[irmao.n] = idsAntigos[i];
                irmao.posicoes[irmao.n] = posAntigas[i];
                irmao.n++;
            }
        }

        long enderecoAntigo = cheio.endereco;
        escreverBucket(cheio); // reescreve no mesmo endereco (endereco != -1)
        escreverBucket(irmao); // bucket novo, vai para o fim do arquivo de buckets

        // repassa, no diretorio, os ponteiros de quem apontava para o bucket
        // dividido e cujo bit novo aponta para o lado do irmao
        for (int j = 0; j < diretorio.length; j++) {
            if (diretorio[j] == enderecoAntigo) {
                int bit = (j >> antigaLocal) & 1;
                if (bit == 1) diretorio[j] = irmao.endereco;
            }
        }
        persistirDiretorio();
    }

    // Dobra o diretorio (profundidade global + 1): cada ponteiro passa a
    // ocupar 2 posicoes seguidas, ambas apontando para o mesmo bucket de antes
    private void dobrarDiretorio() {
        long[] novo = new long[diretorio.length * 2];
        for (int j = 0; j < diretorio.length; j++) {
            novo[j] = diretorio[j];
            novo[j + diretorio.length] = diretorio[j];
        }
        diretorio = novo;
        profundidadeGlobal++;
    }


    //  ATUALIZACAO E REMOCAO

    // Troca a posicao associada a um id (usado quando o registro muda de
    // lugar no arquivo de dados, ex: update que aumenta de tamanho)
    public boolean atualizar(int id, long novaPosicao) throws IOException {
        int idx = indiceDiretorio(id);
        Bucket b = lerBucket(diretorio[idx]);
        for (int i = 0; i < b.n; i++) {
            if (b.ids[i] == id) {
                b.posicoes[i] = novaPosicao;
                escreverBucket(b);
                return true;
            }
        }
        return false;
    }

    // Remove o id do indice (o ultimo par do bucket ocupa o lugar do
    // removido). Buckets irmaos nao sao fundidos de volta ao remover: e uma
    // simplificacao razoavel aqui, ja que a ordenacao externa reconstroi
    // todos os indices do zero (reconstruirIndices), o que reequilibra tudo.
    public boolean remover(int id) throws IOException {
        int idx = indiceDiretorio(id);
        Bucket b = lerBucket(diretorio[idx]);
        for (int i = 0; i < b.n; i++) {
            if (b.ids[i] == id) {
                b.ids[i] = b.ids[b.n - 1];
                b.posicoes[i] = b.posicoes[b.n - 1];
                b.n--;
                escreverBucket(b);
                return true;
            }
        }
        return false;
    }


    //  ACESSO AOS ARQUIVOS

    // Le um bucket inteiro de uma vez e converte os bytes nos campos
    private Bucket lerBucket(long endereco) throws IOException {
        byte[] ba = new byte[tamBucketBytes];
        bck.seek(endereco);
        bck.readFully(ba);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(ba));

        Bucket b = new Bucket(capacidade);
        b.endereco = endereco;
        b.profundidadeLocal = dis.readInt();
        b.n = dis.readInt();
        for (int i = 0; i < capacidade; i++) b.ids[i] = dis.readInt();
        for (int i = 0; i < capacidade; i++) b.posicoes[i] = dis.readLong();
        return b;
    }

    // Grava o bucket no seu endereco; bucket novo vai para o fim do arquivo
    private void escreverBucket(Bucket b) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(b.profundidadeLocal);
        dos.writeInt(b.n);
        for (int i = 0; i < capacidade; i++) dos.writeInt(b.ids[i]);
        for (int i = 0; i < capacidade; i++) dos.writeLong(b.posicoes[i]);

        if (b.endereco == -1) b.endereco = bck.length();
        bck.seek(b.endereco);
        bck.write(baos.toByteArray());
    }

    // Reescreve o cabecalho e todos os ponteiros do diretorio. O arquivo e
    // pequeno (no maximo alguns milhares de longs), entao reescrever ele
    // inteiro a cada divisao/duplicacao e simples e barato.
    private void persistirDiretorio() throws IOException {
        dir.seek(0);
        dir.setLength(0);
        dir.writeInt(capacidade);
        dir.writeInt(profundidadeGlobal);
        for (long endereco : diretorio) dir.writeLong(endereco);
    }

    public void fechar() throws IOException {
        dir.close();
        bck.close();
    }
}