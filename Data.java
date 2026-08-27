public class Data{
    int mes;
    int dia;
    int ano;

    public Data(String s){
        String m = "";
        m += s.charAt(0) + s.charAt(1);
        String d = "";
        d += s.charAt(3) + s.charAt(4);
        String a = "";
        a += s.charAt(6) + s.charAt(7) + s.charAt(8) + s.charAt(9);

        int mes = Integer.parseInt(m);
        int dia = Integer.parseInt(d);
        int ano = Integer.parseInt(a);

        this.mes = mes;
        this.dia = dia;
        this.ano = ano;
    }

    public Data(int mes, int dia, int ano){
        this.mes = mes;
        this.dia = dia;
        this.ano = ano;
    }
}
