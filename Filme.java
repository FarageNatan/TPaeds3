public class Filme{
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

    public Filme(int i, String n, Data lan, float nota, String[]gen, String over, String[]el, String t, String stat,
        String[]idm, float orc){
        id = i;
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
    }
}
