import java.util.List;

//  Interface comum dos algoritmos de CASAMENTO DE PADRAO (Etapa 3).
//
//  O padrao e informado uma unica vez, no construtor (a fase de pre-processamento
//  - tabela de falhas do KMP, tabelas do Boyer-Moore - e feita ali). Depois o
//  mesmo objeto e reaproveitado para varrer o texto de milhares de registros.
//
//  Alem das posicoes encontradas, cada buscador conta quantas COMPARACOES DE
//  CARACTERES fez, para comparar os algoritmos de forma independente da maquina.

public interface BuscadorPadrao {

    // Nome do algoritmo, para exibir ao usuario
    String nome();

    // Devolve a posicao inicial (indice em "texto") de TODAS as ocorrencias do
    // padrao, inclusive as que se sobrepoem ("aa" em "aaa" ocorre 2 vezes)
    List<Integer> buscarTodos(String texto);

    // Total de comparacoes de caracteres feitas desde a criacao do buscador
    long getComparacoes();
}
