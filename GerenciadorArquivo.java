import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;


public class GerenciadorArquivo {
    private RandomAccessFile raf;
    private final String nomeArq = "filmes.db";

    // Criterio de ordenacao da ordenacao externa: por id crescente
    private static final Comparator<Filme> POR_ID = (a, b) -> Integer.compare(a.id, b.id);

    public GerenciadorArquivo(){
        try{
            boolean arquivoExiste = new File(nomeArq).exists();
            raf  = new RandomAccessFile(nomeArq,"rw");

            //Se for um arquivo novo, inicializa o cabeçalho (último ID) com 0
            if(!arquivoExiste){
                raf.writeInt(0);
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    
    // CARGA DA BASE
    // Lê o CSV informado e insere cada linha como um novo registro no arquivo
    // binário, reaproveitando o create() do próprio CRUD
    // Retorna a quantidade de filmes efetivamente carregados
    
    public int carregarBaseCSV(String caminhoArquivo){
        System.out.println("Iniciando a carga de dados...");
        int registrosCarregados = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine(); //descarta o cabeçalho

            while ((linha = br.readLine()) != null) {
                // Separa pelas vírgulas, ignorando as vírgulas dentro de aspas duplas
                String[] colunas = linha.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                try {
                    String nome = limparAspas(colunas[0]);
                    String dataS = limparAspas(colunas[1]);
                    float nota = colunas[2].isEmpty() ? 0 : Float.parseFloat(limparAspas(colunas[2]));
                    String[] genero = limparAspas(colunas[3]).split(",");
                    String overview = limparAspas(colunas[4]);
                    String[] elenco = limparAspas(colunas[5]).split(",");
                    String titulo = limparAspas(colunas[6]);
                    String status = limparAspas(colunas[7]);
                    String[] idiomas = limparAspas(colunas[8]).split(",");

                    // Validações defensivas para Orçamento, Receita (revenue) e País (country)
                    float orcamento = 0f;
                    if(colunas.length > 9 && !colunas[9].isEmpty()) {
                        try { orcamento = Float.parseFloat(limparAspas(colunas[9])); } catch (Exception ignore){}
                    }

                    float revenue = 0f;
                    if(colunas.length > 10 && !colunas[10].isEmpty()) {
                        try { revenue = Float.parseFloat(limparAspas(colunas[10])); } catch (Exception ignore){}
                    }

                    String country = "";
                    if(colunas.length > 11) {
                        country = limparAspas(colunas[11]);
                    }

                    Data dataLancamento = new Data(dataS);

                    Filme filme = new Filme(nome, dataLancamento, nota, genero, overview, elenco, titulo, status, idiomas, orcamento, revenue, country);

                    // Salva no arquivo binário chamando o Create do CRUD
                    create(filme);
                    registrosCarregados++;

                } catch (Exception e) {
                    System.out.println("Aviso: Linha mal formatada no CSV foi pulada. Conteúdo: " + linha);
                }
            }
            System.out.println("Carga concluída com sucesso! " + registrosCarregados + " filmes cadastrados.");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return registrosCarregados;
    }

    private String limparAspas(String texto) {
        return texto.replace("\"", "").trim();
    }

    //CREATE
    //Escreve um novo registro no final do arquivo.

    public void create(Filme filme) throws IOException{
        raf.seek(0); //mover o ponteiro para início do arquivo (cabeçalho)
        int ultimoId = raf.readInt();
        ultimoId++;
        filme.id = ultimoId; //Construtor passa o id como 0 ou lixo, atribuimos então o id correto aqui

        raf.seek(0);
        raf.writeInt(ultimoId); //sobrescreve o valor do cabeçalho com o novo último id

        raf.seek(raf.length());
        byte[] ba = filme.toByteArray(); //Serialização

        raf.writeBoolean(false);
        raf.writeInt(ba.length);
        raf.write(ba);
    }


    // READ
    // Lê um registro através de seu ID realizando uma busca sequencial.
    
    public Filme read(int idProc) throws IOException{
        raf.seek(4); //pula o cabeçalho

        while(raf.getFilePointer() < raf.length()){
            boolean lapide = raf.readBoolean(); //Confere se eh valido
            int tamanho = raf.readInt(); //Tamanho do registro

            if(!lapide){
                byte[] ba = new byte[tamanho];
                raf.read(ba);

                Filme filmetmp = new Filme();
                filmetmp.fromByteArray(ba);

                if(filmetmp.id == idProc){
                    return filmetmp;
                }
            }else {
                raf.skipBytes(tamanho); //registro inválido, pulamos
            }
        }

        return null;
    }

    public boolean update(Filme novoFilme) throws IOException {
        raf.seek(4);

        while(raf.getFilePointer() < raf.length()){
            long pos = raf.getFilePointer();
            boolean lapide = raf.readBoolean();
            int tamAntigo = raf.readInt();

            if(!lapide){
                byte[] baAntigo = new byte[tamAntigo];
                raf.read(baAntigo);

                Filme filmetmp = new Filme();
                filmetmp.fromByteArray(baAntigo);
                if(filmetmp.id == novoFilme.id){
                    byte[] baNovo = novoFilme.toByteArray();
                    int tamanhoNovo = baNovo.length;

                    if(tamanhoNovo <= tamAntigo){  //se o novo registro diminuir ou manter o tamanho do antigo, escreve na memsma posição
                        raf.seek(pos + 5);
                        raf.write(baNovo);
                        return true;
                    }else{ //novo registro aumentou de tamanho, devemos invalidar o antigo e escrever o novo ao final do arquivo
                        raf.seek(pos);
                        raf.writeBoolean(true);

                        //processo do create, mas sem mexer no id
                        raf.seek(raf.length());
                        raf.writeBoolean(false);
                        raf.writeInt(tamanhoNovo);
                        raf.write(baNovo);
                        return true;
                    }
                }
            }else{
                raf.skipBytes(tamAntigo);
            }
        }
        return false;
    }

    public boolean delete(int id) throws IOException{
        raf.seek(4); // Pula o cabeçalho

        while (raf.getFilePointer() < raf.length()) {
            long posicaoRegistro = raf.getFilePointer(); // Guarda a posição onde a lápide deste registro se encontra
            boolean lapide = raf.readBoolean();
            int tamanho = raf.readInt();

            if (!lapide) {
                byte[] ba = new byte[tamanho];
                raf.read(ba);

                Filme filmeTemp = new Filme();
                filmeTemp.fromByteArray(ba);

                if (filmeTemp.id == id) {
                    // Encontrou! Agora voltamos na posição guardada e marcamos a lápide
                    raf.seek(posicaoRegistro);
                    raf.writeBoolean(true); // True = excluído (lápide ativada)
                    return true;
                }
            } else {
                // Pula o tamanho do vetor de bytes para avançar ao próximo
                raf.skipBytes(tamanho);
            }
        }
            return false;
    }


    //  ORDENACAO EXTERNA
    //  Intercalacao balanceada de B caminhos, com duas otimizacoes:
    //    - SELECAO POR SUBSTITUICAO na geracao dos segmentos
    //      => blocos de tamanho VARIAVEL (em media ~2x a memoria)
    //    - FILA DE PRIORIDADES (PriorityQueue) na intercalacao
    //      => escolhe o menor id em O(log B) por registro

    // Item do heap da selecao por substituicao: registro + marca do segmento a que pertence
    private static class Entrada {
        final Filme filme;
        final int marca;
        Entrada(Filme filme, int marca) { this.filme = filme; this.marca = marca; }
    }

    // Ordena primeiro pela marca do segmento, depois pelo id
    private static final Comparator<Entrada> POR_MARCA_ID = (x, y) ->
            (x.marca != y.marca) ? Integer.compare(x.marca, y.marca) : Integer.compare(x.filme.id, y.filme.id);

    // Cursor de leitura de um segmento de fita durante a intercalacao
    private static class Cursor {
        final DataInputStream in;
        int restante;
        Filme atual;
        Cursor(DataInputStream in, int totalRegistros) { this.in = in; this.restante = totalRegistros; }

        // Le o proximo registro do segmento; devolve false quando o segmento acaba
        boolean avancar() throws IOException {
            if (restante <= 0) { atual = null; return false; }
            atual = lerTmp(in);
            restante--;
            return true;
        }
    }

    /**
     * Ordena o arquivo de dados por ID.
     *
     * Fase 1 (distribuicao) - le o arquivo e vai gerando segmentos ordenados por
     * SELECAO POR SUBSTITUICAO: mantem um heap de ate "maxRegistros" registros em
     * memoria primaria; ao remover o menor, escreve-o no segmento corrente e le o
     * proximo do arquivo - se ele ainda cabe no segmento corrente (id >= ultimo
     * gravado) entra no heap normalmente, senao entra "marcado" para o proximo
     * segmento. Assim os segmentos tem TAMANHO VARIAVEL, em media ~2x a memoria,
     * o que reduz o numero de passadas de intercalacao. Os segmentos sao
     * distribuidos entre "numCaminhos" fitas temporarias, alternando.
     *
     * Fase 2 (intercalacao) - mescla "numCaminhos" segmentos por vez usando uma
     * FILA DE PRIORIDADES (menor id no topo), alternando as fitas de saida, ate
     * restar um unico segmento.
     *
     * Registros com lapide (deletados) e o espaco desperdicado por atualizacoes
     * sao descartados: o arquivo final fica ordenado e compactado, e o CRUD
     * seguinte opera sobre ele.
     *
     * @param numCaminhos  arquivos temporarios por lado (>= 2)
     * @param maxRegistros tamanho do heap da selecao por substituicao (>= 1)
     */
    public void ordenacaoExterna(int numCaminhos, int maxRegistros) {
        if (numCaminhos < 2) {
            System.out.println("Numero de caminhos deve ser no minimo 2.");
            return;
        }
        if (maxRegistros < 1) {
            System.out.println("Tamanho do heap em memoria deve ser no minimo 1.");
            return;
        }

        final int b = numCaminhos;
        String[] fitasA = new String[b];
        String[] fitasB = new String[b];
        for (int i = 0; i < b; i++) {
            fitasA[i] = "ord_tmp_A" + i + ".dat";
            fitasB[i] = "ord_tmp_B" + i + ".dat";
        }

        try {
            // 1) DISTRIBUICAO (selecao por substituicao)
            List<List<Integer>> segs = new ArrayList<>();
            for (int i = 0; i < b; i++) segs.add(new ArrayList<>());

            int totalRegistros = 0;

            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(nomeArq)))) {

                dis.readInt(); // consome o cabecalho (ultimo id)

                DataOutputStream[] out = new DataOutputStream[b];
                for (int i = 0; i < b; i++) {
                    out[i] = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fitasA[i])));
                }

                PriorityQueue<Entrada> heap = new PriorityQueue<>(maxRegistros, POR_MARCA_ID);

                // preenche o heap com os primeiros registros validos
                for (int i = 0; i < maxRegistros; i++) {
                    Filme f = proximoValido(dis);
                    if (f == null) break;
                    heap.add(new Entrada(f, 0));
                }

                int fita = 0;
                int marcaAtual = 0;
                int qtdSegmento = 0;

                while (!heap.isEmpty()) {
                    Entrada e = heap.poll();

                    // troca de segmento: o registro removido pertence ao proximo
                    if (e.marca != marcaAtual) {
                        segs.get(fita).add(qtdSegmento);
                        qtdSegmento = 0;
                        fita = (fita + 1) % b;
                        marcaAtual = e.marca;
                    }

                    escreverTmp(out[fita], e.filme);
                    qtdSegmento++;
                    totalRegistros++;

                    Filme prox = proximoValido(dis);
                    if (prox != null) {
                        int marca = (POR_ID.compare(prox, e.filme) >= 0) ? marcaAtual : marcaAtual + 1;
                        heap.add(new Entrada(prox, marca));
                    }
                }
                if (qtdSegmento > 0) segs.get(fita).add(qtdSegmento);

                for (DataOutputStream o : out) o.close();
            }

            int totalSegmentos = 0;
            for (List<Integer> s : segs) totalSegmentos += s.size();

            if (totalRegistros == 0) {
                System.out.println("Nenhum registro valido para ordenar.");
                apagarTemporarios(fitasA, fitasB);
                return;
            }

            System.out.println("Distribuicao (selecao por substituicao): " + totalRegistros
                    + " registro(s) em " + totalSegmentos + " segmento(s) de tamanho variavel "
                    + tamanhosDosSegmentos(segs) + ".");

            // 2) INTERCALACAO (fila de prioridades)
            String[] entrada = fitasA;
            String[] saida = fitasB;
            int passada = 0;

            while (totalSegmentos > 1) {
                passada++;

                DataInputStream[] in = new DataInputStream[b];
                DataOutputStream[] out = new DataOutputStream[b];
                for (int i = 0; i < b; i++) {
                    in[i] = new DataInputStream(new BufferedInputStream(new FileInputStream(entrada[i])));
                    out[i] = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(saida[i])));
                }

                List<List<Integer>> segsSaida = new ArrayList<>();
                for (int i = 0; i < b; i++) segsSaida.add(new ArrayList<>());

                int maxRodadas = 0;
                for (List<Integer> s : segs) maxRodadas = Math.max(maxRodadas, s.size());

                int fitaSaida = 0;
                for (int r = 0; r < maxRodadas; r++) {
                    // um cursor por fita que ainda tenha segmento nesta rodada
                    PriorityQueue<Cursor> pq =
                            new PriorityQueue<>(b, (x, y) -> POR_ID.compare(x.atual, y.atual));

                    for (int i = 0; i < b; i++) {
                        if (r < segs.get(i).size()) {
                            Cursor c = new Cursor(in[i], segs.get(i).get(r));
                            c.avancar(); // le o 1o registro do segmento
                            pq.add(c);
                        }
                    }

                    int gravados = 0;
                    while (!pq.isEmpty()) {
                        Cursor c = pq.poll();
                        escreverTmp(out[fitaSaida], c.atual);
                        gravados++;
                        if (c.avancar()) pq.add(c);
                    }

                    if (gravados > 0) {
                        segsSaida.get(fitaSaida).add(gravados);
                        fitaSaida = (fitaSaida + 1) % b;
                    }
                }

                for (DataInputStream i : in) i.close();
                for (DataOutputStream o : out) o.close();

                // troca os papeis: a saida desta passada e a entrada da proxima
                String[] tmp = entrada; entrada = saida; saida = tmp;
                segs = segsSaida;

                totalSegmentos = 0;
                for (List<Integer> s : segs) totalSegmentos += s.size();

                System.out.println("Passada " + passada + ": " + totalSegmentos + " segmento(s) restante(s).");
            }

            // 3) GRAVA O NOVO ARQUIVO ORDENADO
            int fitaFinal = 0;
            while (fitaFinal < b && segs.get(fitaFinal).isEmpty()) fitaFinal++;
            int qtdFinal = segs.get(fitaFinal).get(0);

            raf.seek(0);
            int ultimoId = raf.readInt(); // preserva o gerador de ids do cabecalho

            String arqOrdenado = "filmes_ordenado.db";
            try (DataInputStream fin = new DataInputStream(new BufferedInputStream(new FileInputStream(entrada[fitaFinal])));
                 DataOutputStream fout = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(arqOrdenado)))) {

                fout.writeInt(ultimoId);
                for (int i = 0; i < qtdFinal; i++) {
                    Filme f = lerTmp(fin);
                    byte[] ba = f.toByteArray();
                    fout.writeBoolean(false); // registro valido
                    fout.writeInt(ba.length);
                    fout.write(ba);
                }
            }

            // Substitui o arquivo de dados pelo ordenado (via backup, para nao perder dados)
            raf.close();
            File antigo = new File(nomeArq);
            File ordenado = new File(arqOrdenado);
            File backup = new File("filmes_bkp.db");
            backup.delete();

            if (!antigo.renameTo(backup)) {
                throw new IOException("nao foi possivel mover o arquivo atual para backup.");
            }
            if (!ordenado.renameTo(antigo)) {
                backup.renameTo(antigo); // desfaz
                throw new IOException("nao foi possivel ativar o arquivo ordenado.");
            }
            backup.delete();

            raf = new RandomAccessFile(nomeArq, "rw"); // CRUD seguinte opera no novo arquivo
            apagarTemporarios(fitasA, fitasB);

            System.out.println("Ordenacao externa concluida! " + qtdFinal
                    + " registro(s) ordenado(s) por ID em " + passada + " passada(s) de intercalacao.");

        } catch (IOException e) {
            System.out.println("Erro na ordenacao externa: " + e.getMessage());
            garantirArquivoAberto();
            apagarTemporarios(fitasA, fitasB);
        }
    }

    // Le o proximo registro NAO marcado com lapide; devolve null no fim do arquivo
    private static Filme proximoValido(DataInputStream dis) throws IOException {
        while (true) {
            boolean lapide;
            try {
                lapide = dis.readBoolean();
            } catch (EOFException eof) {
                return null;
            }
            int tam = dis.readInt();
            byte[] ba = new byte[tam];
            dis.readFully(ba);
            if (!lapide) {
                Filme f = new Filme();
                f.fromByteArray(ba);
                return f;
            }
            // lapide ativa: registro descartado, segue para o proximo
        }
    }

    // Grava um registro no formato das fitas temporarias: [tamanho:int][dados]
    private static void escreverTmp(DataOutputStream dos, Filme f) throws IOException {
        byte[] ba = f.toByteArray();
        dos.writeInt(ba.length);
        dos.write(ba);
    }

    // Le um registro de uma fita temporaria (o chamador controla quantos ler por segmento)
    private static Filme lerTmp(DataInputStream dis) throws IOException {
        int tam = dis.readInt();
        byte[] ba = new byte[tam];
        dis.readFully(ba);
        Filme f = new Filme();
        f.fromByteArray(ba);
        return f;
    }

    // Monta uma string com os tamanhos dos segmentos, para exibir a variabilidade dos blocos
    private static String tamanhosDosSegmentos(List<List<Integer>> segs) {
        List<Integer> todos = new ArrayList<>();
        for (List<Integer> s : segs) todos.addAll(s);
        return todos.toString();
    }

    // Remove os arquivos temporarios da ordenacao externa.
    private void apagarTemporarios(String[] fitasA, String[] fitasB) {
        for (String nome : fitasA) new File(nome).delete();
        for (String nome : fitasB) new File(nome).delete();
    }

    // Reabre o RandomAccessFile do arquivo de dados caso tenha sido fechado
    private void garantirArquivoAberto() {
        try {
            raf.getFilePointer(); // lanca IOException se o stream estiver fechado
        } catch (IOException e) {
            try {
                raf = new RandomAccessFile(nomeArq, "rw");
                if (raf.length() == 0) raf.writeInt(0);
            } catch (IOException ignore) {
                ignore.printStackTrace();
            }
        }
    }
}
