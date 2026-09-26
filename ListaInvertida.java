import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

//  LISTA INVERTIDA
//  Associa cada TERMO (ex: o genero "drama" ou o pais "us") a lista de ids
//  dos filmes que possuem esse termo. Os ids encontrados sao depois usados na
//  Arvore B+ para chegar ao registro no arquivo de dados.
//
//  Cada lista usa dois arquivos:
//    - dicionario (<nome>.dic): pares [termo:UTF][endereco do 1o bloco:long].
//      O vocabulario e pequeno, entao e carregado inteiro em memoria ao abrir;
//      termos novos sao anexados ao fim do arquivo.
//    - blocos (<nome>.blc): blocos de tamanho fixo, encadeados por termo:
//      [quantidade:int][ids: TAM_BLOCO int][proximo:long]
//      Quando um bloco enche, um novo e criado no fim do arquivo e ligado ao ultimo.

public class ListaInvertida {
    static final int TAM_BLOCO = 64; // quantidade de ids por bloco
    private static final int TAM_BLOCO_BYTES = 4 + 4 * TAM_BLOCO + 8;

    private final RandomAccessFile dic;
    private final RandomAccessFile blocos;
    private final Map<String, Long> dicionario = new HashMap<>(); // termo -> 1o bloco

    // Bloco em memoria
    private static class Bloco {
        long endereco = -1;  // -1 = bloco novo, ainda nao gravado
        int n = 0;
        int[] ids = new int[TAM_BLOCO];
        long proximo = -1;
    }

    // Abre (ou cria) os dois arquivos da lista e carrega o dicionario
    public ListaInvertida(String nomeBase) throws IOException {
        dic = new RandomAccessFile(nomeBase + ".dic", "rw");
        blocos = new RandomAccessFile(nomeBase + ".blc", "rw");

        dic.seek(0);
        while (dic.getFilePointer() < dic.length()) {
            String termo = dic.readUTF();
            long endereco = dic.readLong();
            dicionario.put(termo, endereco);
        }
    }

    // Padroniza o termo: minusculo e sem espacos nas pontas (inclui o espaco nao separavel do CSV)
    public static String normalizar(String termo) {
        return termo.replace(' ', ' ').trim().toLowerCase();
    }


    //  OPERACOES

    // Adiciona o id na lista do termo; devolve false se ja estava la
    public boolean inserir(String termo, int id) throws IOException {
        termo = normalizar(termo);
        if (termo.isEmpty()) return false;

        Long inicio = dicionario.get(termo);
        if (inicio == null) {
            // termo novo: cria o 1o bloco ja com o id e registra no dicionario
            Bloco b = new Bloco();
            b.ids[0] = id;
            b.n = 1;
            escreverBloco(b);
            dicionario.put(termo, b.endereco);
            dic.seek(dic.length());
            dic.writeUTF(termo);
            dic.writeLong(b.endereco);
            return true;
        }

        // percorre a lista toda: confere duplicata e guarda o 1o bloco com espaco livre
        Bloco livre = null;
        Bloco ultimo = null;
        long end = inicio;
        while (end != -1) {
            Bloco b = lerBloco(end);
            for (int i = 0; i < b.n; i++) {
                if (b.ids[i] == id) return false;
            }
            if (livre == null && b.n < TAM_BLOCO) livre = b;
            ultimo = b;
            end = b.proximo;
        }

        if (livre != null) {
            livre.ids[livre.n++] = id;
            escreverBloco(livre);
        } else {
            // todos os blocos cheios: cria um novo e liga ao ultimo
            Bloco novo = new Bloco();
            novo.ids[0] = id;
            novo.n = 1;
            escreverBloco(novo);
            ultimo.proximo = novo.endereco;
            escreverBloco(ultimo);
        }
        return true;
    }

    // Retira o id da lista do termo (o ultimo id do bloco ocupa o lugar dele)
    public boolean remover(String termo, int id) throws IOException {
        Long end = dicionario.get(normalizar(termo));
        if (end == null) return false;

        while (end != -1) {
            Bloco b = lerBloco(end);
            for (int i = 0; i < b.n; i++) {
                if (b.ids[i] == id) {
                    b.ids[i] = b.ids[b.n - 1];
                    b.n--;
                    escreverBloco(b);
                    return true;
                }
            }
            end = b.proximo;
        }
        return false;
    }

    // Devolve todos os ids associados ao termo (lista vazia se o termo nao existir)
    public List<Integer> buscar(String termo) throws IOException {
        List<Integer> ids = new ArrayList<>();
        Long end = dicionario.get(normalizar(termo));
        if (end == null) return ids;

        while (end != -1) {
            Bloco b = lerBloco(end);
            for (int i = 0; i < b.n; i++) ids.add(b.ids[i]);
            end = b.proximo;
        }
        return ids;
    }

    // Termos cadastrados, em ordem alfabetica (para mostrar as opcoes de busca)
    public Set<String> termos() {
        return new TreeSet<>(dicionario.keySet());
    }


    //  ACESSO AO ARQUIVO DE BLOCOS

    // Le um bloco inteiro de uma vez
    private Bloco lerBloco(long endereco) throws IOException {
        byte[] ba = new byte[TAM_BLOCO_BYTES];
        blocos.seek(endereco);
        blocos.readFully(ba);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(ba));

        Bloco b = new Bloco();
        b.endereco = endereco;
        b.n = dis.readInt();
        for (int i = 0; i < TAM_BLOCO; i++) b.ids[i] = dis.readInt();
        b.proximo = dis.readLong();
        return b;
    }

    // Grava o bloco no seu endereco; bloco novo vai para o fim do arquivo
    private void escreverBloco(Bloco b) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(TAM_BLOCO_BYTES);
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(b.n);
        for (int i = 0; i < TAM_BLOCO; i++) dos.writeInt(b.ids[i]);
        dos.writeLong(b.proximo);

        if (b.endereco == -1) b.endereco = blocos.length();
        blocos.seek(b.endereco);
        blocos.write(baos.toByteArray());
    }

    public void fechar() throws IOException {
        dic.close();
        blocos.close();
    }
}
