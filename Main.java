import java.io.BufferedReader;
import java.io.FileReader;

public class Main {
    public static void main(String[] args) {
        GerenciadorArquivo gerenciador = new GerenciadorArquivo();
        
        // Chamada da carga - deixar comentado apos primeira execução
        carregarBaseCSV("imdb_movies.csv", gerenciador);
    }

    public static void carregarBaseCSV(String caminhoArquivo, GerenciadorArquivo gerenciador) {
        System.out.println("Iniciando a carga de dados...");
        int registrosCarregados = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine();
            
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
                    
                    // Salva no Arquivo Binário chamando o Create do CRUD
                    gerenciador.create(filme);
                    registrosCarregados++;

                } catch (Exception e) {
                    System.out.println("Aviso: Linha mal formatada no CSV foi pulada. Conteúdo: " + linha);
                }
            }
            System.out.println("Carga concluída com sucesso! " + registrosCarregados + " filmes cadastrados.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String limparAspas(String texto) {
        return texto.replace("\"", "").trim();
    }
}