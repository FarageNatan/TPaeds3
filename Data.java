public class Data{
    int mes;
    int dia;
    int ano;

    public Data(String s){
    int mes = Integer.parseInt(s.substring(0, 2));
    int dia = Integer.parseInt(s.substring(3, 5));
    int ano = Integer.parseInt(s.substring(6, 10));

    this.mes = mes;
    this.dia = dia;
    this.ano = ano;
}

    public Data(int mes, int dia, int ano){
        this.mes = mes;
        this.dia = dia;
        this.ano = ano;
    }

    @Override
    public String toString(){
        return String.format("%02d/%02d/%04d", mes, dia, ano);
    }
}
