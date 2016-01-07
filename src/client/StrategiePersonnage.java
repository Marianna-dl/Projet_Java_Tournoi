package client;


import java.awt.Point;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.logging.Level;

import client.controle.Console;
import logger.LoggerProjet;
import serveur.IArene;
import serveur.element.Caracteristique;
import serveur.element.Personnage;
import serveur.vuelement.VuePersonnage;
import utilitaires.Calculs;
import utilitaires.Constantes;

/**
 * Strategie d'un personnage. 
 */
public class StrategiePersonnage {
	
	/**
	 * Console permettant d'ajouter une phrase et de recuperer le serveur 
	 * (l'arene).
	 */
	protected Console console;
	private static final int VIEFAIBLE =40;
	//attribut
	private HashMap<Caracteristique,Integer> tabClairvoyance[];
	private ArrayList<Integer> potionsMauvaises;
	private Personnage perso;
	
	protected StrategiePersonnage(LoggerProjet logger){
		logger.info("Lanceur", "Creation de la console...");

	}


	/**
	 * Cree un personnage, la console associe et sa strategie.
	 * @param ipArene ip de communication avec l'arene
	 * @param port port de communication avec l'arene
	 * @param ipConsole ip de la console du personnage
	 * @param nom nom du personnage
	 * @param groupe groupe d'etudiants du personnage
	 * @param nbTours nombre de tours pour ce personnage (si negatif, illimite)
	 * @param position position initiale du personnage dans l'arene
	 * @param logger gestionnaire de log
	 */
	public StrategiePersonnage(String ipArene, int port, String ipConsole, 
			String nom, String groupe, HashMap<Caracteristique, Integer> caracts,
			int nbTours, Point position, LoggerProjet logger) {
		this(logger);
		perso = new Personnage(nom, groupe, caracts);
		tabClairvoyance = new HashMap[99];
		for(int i=0;i<99;++i)
			tabClairvoyance[i]=null;
		
		potionsMauvaises = new ArrayList<Integer>();
		
		try {
			console = new Console(ipArene, port, ipConsole, this, 
					perso, 
					nbTours, position, logger);
			logger.info("Lanceur", "Creation de la console reussie");
			
		} catch (Exception e) {
			logger.info("Personnage", "Erreur lors de la creation de la console : \n" + e.toString());
			e.printStackTrace();
		}
	}

	// TODO etablir une strategie afin d'evoluer dans l'arene de combat
	// une proposition de strategie (simple) est donnee ci-dessous
	/** 
	 * Decrit la strategie.
	 * Les methodes pour evoluer dans le jeu doivent etre les methodes RMI
	 * de Arene et de ConsolePersonnage. 
	 * @param voisins element voisins de cet element (elements qu'il voit)
	 * @throws RemoteException
	 */
	public void executeStrategie(HashMap<Integer, Point> voisins) throws RemoteException {
		// arene
		IArene arene = console.getArene();
		
		// reference RMI de l'element courant
		int refRMI = 0;
		
		// position de l'element courant
		Point position = null;
		
		try {
			refRMI = console.getRefRMI();
			position = arene.getPosition(refRMI);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		
		
		if (voisins.isEmpty() && arene.caractFromRef(refRMI, Caracteristique.VIE) > VIEFAIBLE) { // je n'ai pas de voisins, j'erre
			console.setPhrase("J'erre...");
			arene.deplace(refRMI, 0); 
			
		}else if(voisins.isEmpty() && arene.caractFromRef(refRMI, Caracteristique.VIE) <= VIEFAIBLE){
			console.setPhrase("Je me soigne...");
			arene.lanceAutoSoin(refRMI);
		}
		else { //A modifier 
			
			int refCible =  Calculs.chercheElementProche(position, voisins);
			int distPlusProche = Calculs.distanceChebyshev(position, arene.getPosition(refCible));
			String elemPlusProche = arene.nomFromRef(refCible);
			//String elemPlusProche = arene.nomFromRef(refCible);

			/********** SI ON EST EN DANGER (VIE FAIBLE) **************/
			if(arene.caractFromRef(refRMI, Caracteristique.VIE) <= VIEFAIBLE ){
				refCible = chercherPotionVieVoisin(voisins, arene, refRMI);

				if(potionsMauvaises.contains(refCible)){ //Mauvaise potion
					//arene.deplace(refRMI,0);
					console.setPhrase("Je me soigne...");
					arene.lanceAutoSoin(refRMI);
				}
				//C'est une potion de vie
				else if(refCible != -1){
					distPlusProche = Calculs.distanceChebyshev(position, arene.getPosition(refCible));
					elemPlusProche = arene.nomFromRef(refCible);
					if(distPlusProche <= Constantes.DISTANCE_MIN_INTERACTION) { // si suffisamment proches
						// j'interagis directement
						arene.ramassePotion(refRMI, refCible);

						
					} else { // si voisins, mais plus eloignes
						// je vais vers le plus proche
						console.setPhrase("Je vais vers mon voisin " + elemPlusProche);
						arene.deplace(refRMI, refCible);

					}		
					
					
				}//C'est un personnage ou un monstre
				else{
					refCible =  Calculs.chercheElementProche(position, voisins);
					distPlusProche = Calculs.distanceChebyshev(position, arene.getPosition(refCible));
					//C'est un monstre, on attaque si possible ou c'est une potion on essaye de la prendre
					if(arene.estMonstreFromRef(refCible) || arene.estPotionFromRef(refCible)){
						if(potionsMauvaises.contains(refCible)){
							//arene.deplace(refRMI,0);
							console.setPhrase("Je me soigne...");
							arene.lanceAutoSoin(refRMI);
						}
						else{
							
							elemPlusProche = arene.nomFromRef(refCible);
							if(distPlusProche <= Constantes.DISTANCE_MIN_INTERACTION) { // si suffisamment proches
								// j'interagis directement
								interagit(arene, refCible, refRMI,elemPlusProche);
	
								
							} else { // si voisins, mais plus eloignes
								// je vais vers le plus proche
								console.setPhrase("Je vais vers mon voisin " + elemPlusProche);
								if(arene.estPotionFromRef(refCible)){
									arene.deplace(refRMI, refCible);
									
								}
								else{
									strategieDeplacement(distPlusProche, arene, refRMI,  refCible);
								}
								
							}	
						}
					} //FIN si c'est un monstre/potion
					else{//C'est un personnage
						if(tabClairvoyance.length !=0 && tabClairvoyance[refCible] != null){
							//verifie qu'il ne peut pas nous tuer avec sa defense 
							//VOIR CODE THIERRY
							//En attendant, on se base que sur la force en trichant, on fuit
							if(arene.elementFromRef(refCible).getCaract(Caracteristique.FORCE) > arene.caractFromRef(refRMI, Caracteristique.VIE) ){
								//on fuit
								if(distPlusProche>3){ //On a le temps de se soigner
									console.setPhrase("Je me soigne...");
									arene.lanceAutoSoin(refRMI);
								}
								else{// Sinon on fuit
									arene.deplace(refRMI, 0); // A l'oppose
								}
							}
							else{
								//on attaque si possible
								if(distPlusProche <= Constantes.DISTANCE_MIN_INTERACTION) { 
									arene.lanceAttaque(refRMI, refCible);
								}
								else{
									strategieDeplacement(distPlusProche, arene, refRMI,  refCible);
								}
								
							}
							
						} // Il n'est pas dans le tableau de clairvoyance
						else if(distPlusProche>3){ //On a le temps de se soigner
							//arene.lanceClairvoyance(refRMI, refCible);
							console.setPhrase("Je me soigne...");
							arene.lanceAutoSoin(refRMI);
						}
						else{// On suppose que le perso est plus fort
							arene.deplace(refRMI, 0); // A l'oppose
						}
						
					}

				}
				
				
				
			}/********** FIN DU DANGER(VIE FAIBLE) **************/
			
		}
	}
	
	
	
/**
 * Fonction qui determine si on peut avancer et attaquer ou attendre
 * @param distance
 * @param arene
 * @param refRMI
 * @param cible
 * @throws RemoteException
 */
public void strategieDeplacement(int distance, IArene arene, int refRMI, int cible) throws RemoteException{
	
	int dist = Calculs.distanceChebyshev( arene.getPosition(refRMI), 
			arene.getPosition(cible));
	
	console.log(Level.WARNING, "DISTANCE ", " distance "+dist);
	
	if(distance == 3){
		arene.deplace(refRMI, cible);
		arene.lanceAttaque(refRMI, cible);
	}
	else if(distance >=4 && distance<=6){
		if(arene.caractFromRef(refRMI, Caracteristique.VIE) <= VIEFAIBLE ){
			console.setPhrase("Je me soigne...");
			arene.lanceAutoSoin(refRMI);
			
		}else{
			arene.lanceClairvoyance(refRMI, cible);
		}
		
	}
	else{
		arene.deplace(refRMI, cible);
	}
	
}

	/**
	 * Cherche la potion qui rend de la vie la plus proche
	 * @param voisins tableau des voisins du personnage
	 * @param perso Personnage qui voit les éléments
	 * @return la potion de vie la plus proche, -1 sinon
	 * @throws RemoteException
	 */
public int chercherPotionVieVoisin(HashMap<Integer, Point> voisins, IArene arene, int refRMI) throws RemoteException{
	int viePotion;
	int forcePerso;
	int initiativePerso;
	int defensePerso;

	HashMap<Integer, Point> potionsProches = new HashMap<Integer, Point>();
	
	for(int refVoisin : voisins.keySet()) {
		if(arene.estPotionFromRef(refVoisin)){
			viePotion =  arene.caractFromRef(refVoisin, Caracteristique.VIE);
			forcePerso = perso.getCaract(Caracteristique.FORCE) + arene.caractFromRef(refVoisin, Caracteristique.FORCE);
			initiativePerso = perso.getCaract(Caracteristique.INITIATIVE) + arene.caractFromRef(refVoisin, Caracteristique.INITIATIVE);
			defensePerso = perso.getCaract(Caracteristique.DEFENSE) + arene.caractFromRef(refVoisin, Caracteristique.DEFENSE);
			if(viePotion>0 && forcePerso > 20 && initiativePerso > 0  && defensePerso>20){
				potionsProches.put(refVoisin, arene.getPosition(refVoisin));
				
			}
			else{
				potionsMauvaises.add(refVoisin);
			}
			
		}
		
	
	}
	
	if(!(potionsProches.isEmpty())){
		Point position = arene.getPosition(refRMI);
		return Calculs.chercheElementProche(position, potionsProches);
	}
	
	return -1;
	
}

/**
 * Permet d'intéragir avec les differents elements
 * @param arene
 * @param refCible
 * @param refRMI
 * @param elemPlusProche
 * @throws RemoteException
 */
public void interagit(IArene arene, int refCible, int refRMI, String elemPlusProche) throws RemoteException{
	
	if(arene.estPotionFromRef(refCible)){ // potion
		if(verifierPotion(arene, refCible)){
			console.setPhrase("Je ramasse une potion");
			arene.ramassePotion(refRMI, refCible);
		}
		else{
			console.setPhrase("C'est du poison !");
			potionsMauvaises.add(refCible);
			console.setPhrase("J'erre...");
			arene.deplace(refRMI, 0); 
		}
		
	} else { // personnage
		// duel
		console.setPhrase("Je fais un duel avec " + elemPlusProche);
		arene.lanceAttaque(refRMI, refCible);
	}
	
}

/**
 * Verifie si la potion fait baisser les caracteristique ou non
 * @param arene
 * @param refCible
 * @return
 * @throws RemoteException
 */
public boolean verifierPotion(IArene arene, int refCible) throws RemoteException{
	int vie;
	int force;
	int initiative;
	int defense;	
	
	vie =  arene.caractFromRef(refCible, Caracteristique.VIE);
	force = arene.caractFromRef(refCible, Caracteristique.FORCE);
	initiative = arene.caractFromRef(refCible, Caracteristique.INITIATIVE);
	defense = arene.caractFromRef(refCible, Caracteristique.DEFENSE);
	
	if(vie>=0 && force >= 0 && defense>=0){
		if(perso.getCaract(Caracteristique.INITIATIVE) + initiative >=20){
			return true;
			
		}
		return false;
		
	}	
	
	return false;
	
}



	
}
