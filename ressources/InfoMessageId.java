package org.dofus.network.game.packets;

public enum InfoMessageId {
    INFOS_2("02", "INFOS", 2), // Tu as appris le métier <b>%1</b>.
    INFOS_3("03", "INFOS", 3), // Tu as appris le sort <b>%1</b>.
    INFOS_6("06", "INFOS", 6), // Position sauvegardée.
    INFOS_18("018", "INFOS", 18), // %1 vous a mis à la porte de sa maison...
    INFOS_20("020", "INFOS", 20), // Tu as dû donner %1 kamas pour pouvoir accéder à ce coffre.
    INFOS_21("021", "INFOS", 21), // Tu as obtenu %1 '<b>%2</b>'.
    INFOS_22("022", "INFOS", 22), // Tu as perdu %1 '%2'.
    INFOS_24("024", "INFOS", 24), // Tu viens de mémoriser un nouveau zaap.
    INFOS_25("025", "INFOS", 25), // Votre familier vous fait la fête!
    INFOS_26("026", "INFOS", 26), // Vous donnez à manger à votre familier alors qu'il n'avait plus faim. Il se force pour vous
    INFOS_27("027", "INFOS", 27), // Vous donnez à manger à répétition à votre familier déjà obèse. Il avale quand même la ress
    INFOS_29("029", "INFOS", 29), // Vous donnez à manger à votre familier. Il semble qu'il avait très faim.
    INFOS_32("032", "INFOS", 32), // Votre familier apprécie le repas.
    INFOS_34("034", "INFOS", 34), // Tu as perdu <b>%1</b> points d'énergie.
    INFOS_36("036", "INFOS", 36), // %1 vient de rejoindre le combat en spectateur.
    INFOS_37("037", "INFOS", 37), // Vous êtes désormais considéré comme absent.
    INFOS_38("038", "INFOS", 38), // Vous n'êtes plus considéré comme absent.
    INFOS_39("039", "INFOS", 39), // <b>%1</b> a activé le mode 'spectateur'.
    INFOS_40("040", "INFOS", 40), // <b>%1</b> a désactivé le mode 'spectateur'.
    INFOS_47("047", "INFOS", 47), // Ton mariage avec %1 a été annulé.
    INFOS_48("048", "INFOS", 48), // Le mariage entre %1 et %2 a été annulé.
    INFOS_50("050", "INFOS", 50), // Vous êtes maintenant en mode invisible.
    INFOS_51("051", "INFOS", 51), // Vous n'êtes plus en mode invisible.
    INFOS_58("058", "INFOS", 58), // Vous ne pouvez pas mettre plus d'objets en vente actuellement...
    INFOS_65("065", "INFOS", 65), // Votre compte en banque a été crédité de <b>%1</b> kamas suite à la vente de <b>%4</b> '<b>
    INFOS_68("068", "INFOS", 68), // Lot de <b>%1 '%2'</b> acheté pour <b>%3</b> kamas.
    INFOS_76("076", "INFOS", 76), // Tu as perdu <b>%1</b> points d' honneur.
    INFOS_80("080", "INFOS", 80), // Vous gagnez %1 points d'honneur en récompense de votre bravoure.
    INFOS_81("081", "INFOS", 81), // Vous perdez %1 points d'honneur suite à cette défaite.
    INFOS_82("082", "INFOS", 82), // Votre bravoure vous a fait grimper au rang %1.
    INFOS_83("083", "INFOS", 83), // Vous avez été dégradé au rang %1.
    INFOS_84("084", "INFOS", 84), // Vous êtes sanctionné par %1 point(s) de déshonneur pour ce combat déshonorant
    INFOS_93("093", "INFOS", 93), // L'équipe n'accepte désormais que les membres du groupe du personnage principal.
    INFOS_94("094", "INFOS", 94), // L'équipe accepte les membres de tous les groupes.
    INFOS_95("095", "INFOS", 95), // L'équipe n'accepte plus de personnages supplémentaires.
    INFOS_96("096", "INFOS", 96), // L'équipe accepte de nouveau des personnages supplémentaires.
    INFOS_103("0103", "INFOS", 103), // Demande d'aide signalée...
    INFOS_104("0104", "INFOS", 104), // Demande d'aide annulée...
    INFOS_115("0115", "INFOS", 115), // Ce canal est restreint pour améliorer sa lisibilité. Vous pourrez envoyer un nouveau messa
    INFOS_118("0118", "INFOS", 118), // Vous n'arrivez pas à assembler correctement les ingrédients, et vous n'arrivez pas à conce
    INFOS_152("0152", "INFOS", 152), // Précédente connexion sur votre compte effectuée le %3/%2/%1 à %4:%5 via l'adresse IP %6
    INFOS_153("0153", "INFOS", 153), // Votre adresse IP actuelle est %1.
    INFOS_183("0183", "INFOS", 183), // Malgré vos talents, la magie n'opère pas et vous sentez l'échec de la transformation.
    INFOS_188("0188", "INFOS", 188), // Et comme d'habitude, c'est à <b>%1</b> que l'on doit cet exploit...
    ERROR_2("12", "ERROR", 2), // Tu n'exerces pas le métier nécessaire
    ERROR_4("14", "ERROR", 4), // Tu ne possèdes pas l'objet nécessaire
    ERROR_9("19", "ERROR", 9), // Tu connais déjà suffisamment de métiers.
    ERROR_11("111", "ERROR", 11), // Tu as déjà appris ce métier.
    ERROR_12("112", "ERROR", 12), // Tu es trop chargé. Jette quelques objets pour pouvoir bouger...
    ERROR_13("113", "ERROR", 13), // Cette action n'est pas autorisée sur cette carte.
    ERROR_14("114", "ERROR", 14), // Le joueur %1 était absent et n'a donc pas reçu votre message.
    ERROR_15("115", "ERROR", 15), // Pour des raisons de maintenances, le serveur va être redémarré dans %1.
    ERROR_19("119", "ERROR", 19), // Tes caractéristiques ne conviennent pas
    ERROR_23("123", "ERROR", 23), // Impossible de devenir marchand : tu n'as aucun objet à vendre...
    ERROR_25("125", "ERROR", 25), // Impossible : il ne peut y avoir plus de %1 vendeurs sur cette carte.
    ERROR_32("132", "ERROR", 32), // Tu ne peux pas avoir plus de %1 maison(s).
    ERROR_39("139", "ERROR", 39), // Ton épouse n'est pas disponible pour l'instant...
    ERROR_40("140", "ERROR", 40), // Ton époux n'est pas disponible pour l'instant...
    ERROR_41("141", "ERROR", 41), // Il n'y a pas de place disponible près de ton épouse.
    ERROR_42("142", "ERROR", 42), // Il n'y a pas de place disponible près de ton époux.
    ERROR_53("153", "ERROR", 53), // Votre familier prend la ressource, la renifle un peu, ne semble pas convaincu et vous la r
    ERROR_54("154", "ERROR", 54), // Vous n'avez pas assez fait attention à votre familier. Il s'est transformé en fantôme !
    ERROR_55("155", "ERROR", 55), // Le niveau de ta guilde ne te permet pas d'avoir plus de %1 membres.
    ERROR_57("157", "ERROR", 57), // Impossible de rejoindre ce combat en mode 'Spectateur'.
    ERROR_72("172", "ERROR", 72), // Cet objet n'est plus disponible à ce prix. Quelqu'un a été plus rapide...
    ERROR_76("176", "ERROR", 76), // Vous n'avez pas assez de kamas pour acquitter la taxe de mise en mode marchand...
    ERROR_78("178", "ERROR", 78), // Votre état et celui de votre mari ne vous permet pas de le rejoindre actuellement.
    ERROR_79("179", "ERROR", 79), // Votre état et celui de votre femme ne vous permet pas de la rejoindre actuellement.
    ERROR_82("182", "ERROR", 82), // Vous n'avez pas assez de kamas pour effectuer cette action.
    ERROR_83("183", "ERROR", 83), // Votre déshonneur ne vous permet pas de faire cette action.
    ERROR_89("189", "ERROR", 89), // Bienvenue sur DOFUS Retro, dans le Monde des Douze !\nRappel : prenez garde, il est interd
    ERROR_91("191", "ERROR", 91), // Action impossible en cours de combat.
    ERROR_94("194", "ERROR", 94), // Impossible de vendre un enclos public.
    ERROR_95("195", "ERROR", 95), // Impossible de vendre un enclos qui ne vous appartient pas.
    ERROR_96("196", "ERROR", 96), // Impossible d'acheter un enclos public.
    ERROR_97("197", "ERROR", 97), // Impossible d'acheter un enclos non en vente.
    ERROR_98("198", "ERROR", 98), // Seul le meneur de la guilde peut acheter des enclos.
    ERROR_101("1101", "ERROR", 101), // Vous ne disposez pas des droits de guildes suffisants pour cette opération.
    ERROR_102("1102", "ERROR", 102), // Cellule cible invalide.
    ERROR_103("1103", "ERROR", 103), // Impossible d'acheter un enclos supplémentaire. Une guilde peut acquérir un enclos après so
    ERROR_124("1124", "ERROR", 124), // A force de trop parler, vous en avez perdu la voix. Vous devriez vous taire pendant les %1
    ERROR_127("1127", "ERROR", 127), // Incarnam ne vous est plus accessible désormais, votre expérience fait de vous un aventurie
    ERROR_135("1135", "ERROR", 135), // Vous devez appartenir à une guilde pour profiter de cette possibilité.
    ERROR_136("1136", "ERROR", 136), // La téléportation vers cette maison de guilde n'a pas été autorisée par son propriétaire.
    ERROR_137("1137", "ERROR", 137), // Vous êtes à court de potion de foyer de guilde.
    ERROR_145("1145", "ERROR", 145), // Il n'y a pas assez de place ici.
    ERROR_159("1159", "ERROR", 159), // Vous êtes à court de potion d'enclos de guilde.
    ERROR_164("1164", "ERROR", 164), // Une sauvegarde du serveur est en cours... Vous pouvez continuer de jouer, mais l'accès au 
    ERROR_165("1165", "ERROR", 165), // La sauvegarde du serveur est terminée. L'accès au serveur est de nouveau possible. Merci d
    ERROR_168("1168", "ERROR", 168), // Vous ne pouvez pas poser plus de %1 percepteur(s) par zone.
    ERROR_169("1169", "ERROR", 169), // Impossible de lancer ce sort : vous ne le possédez pas !
    ERROR_170("1170", "ERROR", 170), // Impossible de lancer ce sort : Vous avez %1 PA disponible(s) et il vous en faut %2 pour ce
    ERROR_171("1171", "ERROR", 171), // Impossible de lancer ce sort : Vous avez une portée de %1 à %2 et vous visez à %3 !
    ERROR_172("1172", "ERROR", 172), // Impossible de lancer ce sort : la cellule visée n'est pas disponible !
    ERROR_173("1173", "ERROR", 173), // Impossible de lancer ce sort autrement qu'en ligne droite !
    ERROR_174("1174", "ERROR", 174), // Impossible de lancer ce sort : un obstacle gène votre vue !
    ERROR_175("1175", "ERROR", 175), // Impossible de lancer ce sort actuellement.
    ERROR_180("1180", "ERROR", 180), // Ce percepteur est en train d'être récolté, vous ne pouvez pas l'attaquer.
    ;

    private final String rawCode;
    private final String category;
    private final int messageId;

    InfoMessageId(String rawCode, String category, int messageId) {
        this.rawCode = rawCode;
        this.category = category;
        this.messageId = messageId;
    }

    public String packet(String params) {
        return params == null || params.isEmpty() ? "Im" + rawCode : "Im" + rawCode + ";" + params;
    }
    public String getRawCode() { return rawCode; }
    public String getCategory() { return category; }
    public int getMessageId() { return messageId; }
}