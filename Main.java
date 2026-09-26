import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class Main {

    // Recursos compartilhados por todo o programa
    private static final Scanner sc = new Scanner(System.in);
    private static final GerenciadorArquivo gerenciador = new GerenciadorArquivo();

    public static void main(String[] args) {
        int opcao;

        do {
            exibirMenu();
            opcao = lerInteiro("Escolha uma opcao: ");
            System.out.println();

            switch (opcao) {
                case 1:
                    opcaoCarregarBase();
                    break;
                case 2:
                    opcaoCriarRegistro();
                    break;
                case 3:
                    opcaoLerRegistro();
                    break;
                case 4:
                    opcaoBuscarPorListas();
                    break;
                case 5:
                    opcaoAtualizarRegistro();
                    break;
                case 6:
                    opcaoDeletarRegistro();
                    break;
                case 7:
                    opcaoOrdenacaoExterna();
                    break;
                case 8:
                    opcaoReconstruirIndices();
                    break;
                case 0:
                    System.out.println("Encerrando o programa...");
                    break;
                default:
                    System.out.println("Opcao invalida! Tente novamente.");
            }

            System.out.println();
        } while (opcao != 0);

        gerenciador.fechar();
        sc.close();
    }


    //  MENU


    private static void exibirMenu() {
        System.out.println("==================================================");
        System.out.println("        SISTEMA DE GERENCIAMENTO DE FILMES         ");
        System.out.println("==================================================");
        System.out.println(" 1 - Carregar base de dados (CSV -> arquivo binario)");
        System.out.println(" 2 - Criar um registro        [Arvore B+ e Listas invertidas]");
        System.out.println(" 3 - Ler um registro por ID   [Arvore B+]");
        System.out.println(" 4 - Buscar por genero e/ou pais [Listas invertidas]");
        System.out.println(" 5 - Atualizar um registro    [Arvore B+ e Listas invertidas]");
        System.out.println(" 6 - Deletar um registro      [Arvore B+ e Listas invertidas]");
        System.out.println(" 7 - Ordenacao externa do arquivo (compacta + ordena por ID)");
        System.out.println(" 8 - Reconstruir indices (definir ordem da Arvore B+)");
        System.out.println(" 0 - Sair");
        System.out.println("--------------------------------------------------");
    }


    //  OPCAO 1 - CARGA DA BASE


    private static void opcaoCarregarBase() {
        System.out.println("--- Carga da base de dados ---");
        String caminho = lerString("Caminho do CSV (ENTER para \"imdb_movies.csv\"): ");
        if (caminho.isEmpty()) {
            caminho = "imdb_movies.csv";
        }

        System.out.println("Atencao: a carga ANEXA os registros ao arquivo binario (rodar 2x duplica).");
        String confirma = lerString("Continuar? (S/N): ");
        if (!confirma.equalsIgnoreCase("S")) {
            System.out.println("Carga cancelada.");
            return;
        }

        gerenciador.carregarBaseCSV(caminho);
    }


    //  OPCAO 2 - CRIAR


    private static void opcaoCriarRegistro() {
        System.out.println("--- Criar registro [indices: Arvore B+ e Listas invertidas] ---");

        try {
            Filme novo = lerDadosFilme();
            if (novo == null) {
                return;
            }
            gerenciador.create(novo);
            System.out.println("Filme criado com sucesso! ID gerado: " + novo.id);
        } catch (IOException e) {
            System.out.println("Erro ao acessar o arquivo binario: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Dados invalidos, criacao abortada: " + e.getMessage());
        }
    }


    //  OPCAO 3 - LER


    private static void opcaoLerRegistro() {
        System.out.println("--- Ler registro [indice: Arvore B+] ---");
        int id = lerInteiro("Digite o ID do filme: ");

        try {
            Filme filme = gerenciador.read(id);
            if (filme == null) {
                System.out.println("Nenhum filme encontrado com o ID " + id + ".");
            } else {
                System.out.println();
                System.out.println("--- Dados do filme ---");
                System.out.println(filme);
            }
        } catch (IOException e) {
            System.out.println("Erro ao ler o arquivo binario: " + e.getMessage());
        }
    }


    //  OPCAO 4 - BUSCA PELAS LISTAS INVERTIDAS


    private static void opcaoBuscarPorListas() {
        System.out.println("--- Buscar por genero e/ou pais [indices: Listas invertidas] ---");
        System.out.println("Generos cadastrados: " + String.join(", ", gerenciador.termosGenero()));
        System.out.println("Paises cadastrados: " + String.join(", ", gerenciador.termosPais()));
        System.out.println("Preencha um ou os dois campos (os dois = filmes que atendem aos DOIS).");

        String genero = lerString("Genero (ENTER para ignorar): ");
        String pais = lerString("Pais (ENTER para ignorar): ");
        if (genero.isEmpty() && pais.isEmpty()) {
            System.out.println("Nenhum criterio informado. Busca cancelada.");
            return;
        }

        final int LIMITE = 50; // evita imprimir milhares de linhas no terminal
        try {
            List<Integer> ids = gerenciador.buscarPorListas(genero, pais);
            System.out.println();
            System.out.println(ids.size() + " filme(s) encontrado(s).");

            // cada id vindo da lista invertida e lido pela Arvore B+
            for (int i = 0; i < ids.size() && i < LIMITE; i++) {
                Filme f = gerenciador.read(ids.get(i));
                if (f != null) {
                    System.out.println("ID " + f.id + " | " + f.nome + " | " + f.lancamento
                            + " | " + String.join(", ", f.genero) + " | " + f.pais);
                }
            }
            if (ids.size() > LIMITE) {
                System.out.println("... e mais " + (ids.size() - LIMITE) + " filme(s).");
            }
        } catch (IOException e) {
            System.out.println("Erro ao acessar os indices: " + e.getMessage());
        }
    }


    //  OPCAO 5 - ATUALIZAR


    private static void opcaoAtualizarRegistro() {
        System.out.println("--- Atualizar registro [indices: Arvore B+ e Listas invertidas] ---");
        int id = lerInteiro("Digite o ID do filme a ser atualizado: ");

        try {
            Filme atual = gerenciador.read(id);
            if (atual == null) {
                System.out.println("Nenhum filme encontrado com o ID " + id + ".");
                return;
            }

            System.out.println();
            System.out.println("--- Valores atuais ---");
            System.out.println(atual);

            System.out.println();
            System.out.println("--- Digite os novos valores ---");
            Filme novo = lerDadosFilme();
            if (novo == null) {
                return;
            }
            novo.id = id; // mantém o mesmo ID do registro original

            boolean ok = gerenciador.update(novo);
            if (ok) {
                System.out.println("Filme atualizado com sucesso!");
            } else {
                System.out.println("Nao foi possivel atualizar o filme.");
            }
        } catch (IOException e) {
            System.out.println("Erro ao acessar o arquivo binario: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Dados invalidos, atualizacao abortada: " + e.getMessage());
        }
    }


    //  OPCAO 6 - DELETAR


    private static void opcaoDeletarRegistro() {
        System.out.println("--- Deletar registro [indices: Arvore B+ e Listas invertidas] ---");
        int id = lerInteiro("Digite o ID do filme a ser deletado: ");

        try {
            Filme filme = gerenciador.read(id);
            if (filme == null) {
                System.out.println("Nenhum filme encontrado com o ID " + id + ".");
                return;
            }

            System.out.println();
            System.out.println("Filme encontrado:");
            System.out.println(filme);

            String confirma = lerString("Confirma a exclusao (lapide)? (S/N): ");
            if (!confirma.equalsIgnoreCase("S")) {
                System.out.println("Exclusao cancelada.");
                return;
            }

            boolean ok = gerenciador.delete(id);
            if (ok) {
                System.out.println("Filme deletado com sucesso (lapide marcada).");
            } else {
                System.out.println("Nao foi possivel deletar o filme.");
            }
        } catch (IOException e) {
            System.out.println("Erro ao acessar o arquivo binario: " + e.getMessage());
        }
    }


    //  OPCAO 7 - ORDENACAO EXTERNA


    private static void opcaoOrdenacaoExterna() {
        System.out.println("--- Ordenacao externa (intercalacao balanceada) ---");
        System.out.println("Reescreve o arquivo ordenado por ID, descartando registros");
        System.out.println("deletados (lapide) e o espaco desperdicado por atualizacoes.");
        System.out.println("Otimizacoes: selecao por substituicao (blocos de tamanho");
        System.out.println("variavel) + fila de prioridades na intercalacao.");

        int numCaminhos = lerInteiro("Numero de caminhos (fitas por lado, minimo 2): ");
        int maxRegistros = lerInteiro("Tamanho do heap em memoria - selecao por substituicao (minimo 1): ");

        String confirma = lerString("O CRUD seguinte passara a operar no novo arquivo. Continuar? (S/N): ");
        if (!confirma.equalsIgnoreCase("S")) {
            System.out.println("Ordenacao cancelada.");
            return;
        }

        gerenciador.ordenacaoExterna(numCaminhos, maxRegistros);
    }


    //  OPCAO 8 - RECONSTRUIR INDICES


    private static void opcaoReconstruirIndices() {
        System.out.println("--- Reconstruir indices ---");
        System.out.println("Apaga a Arvore B+ e as listas invertidas e as gera de novo a partir do arquivo de dados.");
        System.out.println("Ordem atual da Arvore B+: " + gerenciador.ordemArvore());

        int ordem = lerInteiro("Nova ordem da Arvore B+ (maximo de filhos por pagina, minimo 3): ");
        gerenciador.reconstruirIndices(ordem);
    }


    //  LEITURA DOS CAMPOS DE UM FILME (usada no criar e no atualizar)
    //  Devolve null se a data (obrigatoria) nao for informada.


    private static Filme lerDadosFilme() {
        String nome      = lerString("Nome: ");

        String dataS     = lerString("Data de lancamento (MM/DD/AAAA): ");
        if (dataS.isEmpty()) {
            System.out.println("Data de lancamento obrigatoria. Operacao cancelada.");
            return null;
        }

        float  nota      = lerFloat("Nota: ");
        String[] genero  = lerLista("Generos (separados por ';'): ");
        String overview  = lerString("Sinopse (overview): ");
        String[] elenco  = lerLista("Elenco (separado por ';'): ");
        String titulo    = lerString("Titulo: ");
        String status    = lerString("Status (ate 15 caracteres, o excedente e cortado): ");
        String[] idiomas = lerLista("Idiomas originais (separados por ';'): ");
        float  orcamento = lerFloat("Orcamento: ");
        float  faturamento = lerFloat("Faturamento: ");
        String pais      = lerString("Pais (codigo de 2 letras, ex: US - o excedente e cortado): ");

        return new Filme(nome, new Data(dataS), nota, genero, overview,
                elenco, titulo, status, idiomas, orcamento, faturamento, pais);
    }


    //  LEITURA DE ENTRADA DO TERMINAL (apoio ao menu)


    private static int lerInteiro(String prompt) {
        while (true) {
            System.out.print(prompt);
            String entrada = sc.nextLine().trim();
            try {
                return Integer.parseInt(entrada);
            } catch (NumberFormatException e) {
                System.out.println("Valor invalido. Digite um numero inteiro.");
            }
        }
    }

    private static float lerFloat(String prompt) {
        while (true) {
            System.out.print(prompt);
            String entrada = sc.nextLine().trim().replace(",", ".");
            if (entrada.isEmpty()) {
                return 0f;
            }
            try {
                return Float.parseFloat(entrada);
            } catch (NumberFormatException e) {
                System.out.println("Valor invalido. Digite um numero (ex: 7.5).");
            }
        }
    }

    private static String lerString(String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }

    private static String[] lerLista(String prompt) {
        String entrada = lerString(prompt);
        if (entrada.isEmpty()) {
            return new String[0];
        }
        String[] partes = entrada.split(";");
        for (int i = 0; i < partes.length; i++) {
            partes[i] = partes[i].trim();
        }
        return partes;
    }
}
