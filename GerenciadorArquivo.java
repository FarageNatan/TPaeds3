import java.io.RandomAccessFile;
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

    /**
     * CREATE
     * Escreve um novo registro no final do arquivo.
     */
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

    /**
     * READ
     * Lê um registro através de seu ID realizando uma busca sequencial.
     */
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
