package fr.hardcore;

/**
 * Etats possibles du jeu.
 *
 * LOBBY        : aucune partie en cours, joueurs dans le hub, en attente.
 * REGENERATING : une partie vient de se terminer, le monde se regenere,
 *                compte a rebours avant la prochaine partie.
 * RUNNING      : partie en cours dans le monde de jeu.
 */
public enum GameState {
    LOBBY,
    REGENERATING,
    RUNNING
}
