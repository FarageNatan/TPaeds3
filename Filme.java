public class Filme{
    int id;
    String nome;
    String rating;
    String genre;
    int year;
    String released;
    float score;
    float votes;
    String director;
    String writer;
    String star;

    public Filme(int i, String n, String rat, String gen, int year, String rel, float score, float votes,
                 String dir, String wri, String star){
        id = i;
        this.nome = n;
        rating = rat;
        genre = gen;
        this.year = year;
        released = rel;
        this.score = score;
        this.votes = votes;
        director = dir;
        writer = wri;
        this.star = star;
    }
}
