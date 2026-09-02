import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.nio.charset.StandardCharsets;

public class Filme{
    static int tam = 0; // contador usado para gerar ids automaticos na criacao de novos filmes (Reinicia a cada execução do programa. Invalido se os registros já foram criados. Possivelmente incluir como metadado no cabeçalho do arquivo criado)
    static final int TAM_STATUS = 15;
    static final int TAM_PAIS   = 2;
    
    int id;
    String nome;
    Data lancamento;
    float nota;
    String[] genero;
    String overview;
    String[] elenco;
    String titulo;
    String status;
    String[] idiomaOr;
    float orcamento;
    float faturamento;
    String pais;

    // Construtor principal: recebe todos os dados do filme e atribui um id automatico
    public Filme(String n, Data lan, float nota, String[]gen, String over, String[]el, String t, String stat,
        String[]idm, float orc, float fat, String p){
        id = tam++;
        this.nome = n;
        lancamento = lan;
        this.nota = nota;
        genero = gen;
        overview = over;
        elenco = el;
        titulo = t;
        status = stat;
        idiomaOr = idm;
        orcamento = orc;
        faturamento = fat;
        pais = p;
    }

    // Construtor vazio: inicializa os campos com valores neutros (usado antes de ler um registro do arquivo)
    public Filme() {
        id = -1;
        this.nome = "";
        lancamento = null;
        this.nota = 0f;
        genero = new String[0];
        overview = "";
        elenco = new String[0];
        titulo = "";
        status = "";
        idiomaOr = new String[0];
        orcamento = 0f;
        faturamento = 0f;
        pais = "";
    }

    // Devolve o filme em formato de texto legivel, juntando os arrays com ", " e formatando o orcamento
    @Override
    public String toString(){
        String generoS = String.join(", ", genero);
        String elencoS = String.join(", ", elenco);
        String idiomaS = String.join(", ", idiomaOr);

        DecimalFormat df = new DecimalFormat("#,##0.00");

        return "ID: " + id + " Nome: " + nome + " Data de Lançamento: " + lancamento + " Nota: " + nota + " Gênero: " + generoS + " Overview: "
            + overview + " Elenco: " + elencoS + " Título: " + titulo + " Status: " + status + " Idiomas Originais: " + idiomaS + " Orçamento: " +
            df.format(orcamento) + " Faturamento: " + df.format(faturamento) + " País: " + pais;
    }

    //Método auxiliar para leitura de strings de tamanho fixo
    private static String readFixed(DataInputStream dis, int n) throws IOException {
        byte[] b = new byte[n];
        dis.readFully(b);
        return new String(b, StandardCharsets.US_ASCII).trim();
    }

    // Converte o filme em um vetor de bytes para ser gravado no arquivo (os arrays viram string separada por ", ")
    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        String lancamentoS = lancamento.toString();

        dos.writeInt(id);
        dos.writeUTF(nome);
        dos.writeUTF(lancamentoS);
        dos.writeFloat(nota);
        dos.writeUTF(String.join(", ", genero));
        dos.writeUTF(overview);
        dos.writeUTF(String.join(", ", elenco));
        dos.writeUTF(titulo);
        dos.writeBytes(String.format("%-" + TAM_STATUS + "s", status).substring(0, TAM_STATUS));
        dos.writeUTF(String.join(", ", idiomaOr));
        dos.writeFloat(orcamento);
        dos.writeFloat(faturamento);
        dos.writeBytes(String.format("%-" + TAM_PAIS   + "s", pais  ).substring(0, TAM_PAIS));

        return baos.toByteArray();
    }

    // Le um vetor de bytes vindo do arquivo e preenche os atributos, na mesma ordem em que foram gravados
    public void fromByteArray(byte[] ba) throws IOException{
        ByteArrayInputStream bais = new ByteArrayInputStream(ba);
        DataInputStream dis = new DataInputStream(bais);

        id = dis.readInt();
        nome = dis.readUTF();
        String lancamentoS = dis.readUTF();
        lancamento = new Data(lancamentoS);
        nota = dis.readFloat();
        String generoS = dis.readUTF();
        genero = generoS.isEmpty() ? new String[0] : generoS.split(", ");
        overview = dis.readUTF();
        String elencoS = dis.readUTF();
        elenco = elencoS.isEmpty() ? new String[0] : elencoS.split(", ");
        titulo = dis.readUTF();
        status = readFixed(dis, TAM_STATUS);
        String idiomaS = dis.readUTF();
        idiomaOr = idiomaS.isEmpty() ? new String[0] : idiomaS.split(", ");
        orcamento = dis.readFloat();
        faturamento = dis.readFloat();
        pais = readFixed(dis, TAM_PAIS);
    }
}