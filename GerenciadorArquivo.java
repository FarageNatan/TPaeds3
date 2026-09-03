import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;


public class GerenciadorArquivo {
    private RandomAccessFile raf;
    private final String nomeArq = "filmes.db";

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

    
      //CARGA DA BASE
      //Lê o CSV informado e insere cada linha como um novo registro no arquivo binário, reaproveitando o create() do próprio CRUD.
      //Retorna a quantidade de filmes efetivamente carregados.
     
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

    
     //READ
     //Lê um registro através de seu ID realizando uma busca sequencial.
     
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
}
