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
import serveur.element.*;

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
			perso=new Personnage (nom,groupe,caracts);
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
		
		
		if (voisins.isEmpty() && arene.caractFromRef(refRMI, Caracteristique.VIE) == 100) { // je n'ai pas de voisins, j'erre
			console.setPhrase("J'erre...");
			arene.deplace(refRMI, 0); 
			
		}else if(voisins.isEmpty() && arene.caractFromRef(refRMI, Caracteristique.VIE) < 100){
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
					
					
				}//C'est une potion ou un monstre
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
							if(!voisinFaible(arene, refRMI, refCible) ){
								boolean distanceVoisins = verifieVoisin(refRMI, refCible, voisins, arene);
								
								//on fuit
								if(distPlusProche>10 && distanceVoisins){ //On a le temps de se soigner
									console.setPhrase("Je me soigne...");
									arene.lanceAutoSoin(refRMI);
								}
								else{// Sinon on fuit
									console.setPhrase("Je fuis...");
									fuite(arene,refRMI,refCible); // A l'oppose
								}
							}
							else{
								//on attaque si possible
								if(distPlusProche <= Constantes.DISTANCE_MIN_INTERACTION) { 
									console.setPhrase("J'attaque...");
									arene.lanceAttaque(refRMI, refCible);
								}
								else{
									strategieDeplacement(distPlusProche, arene, refRMI,  refCible);
								}
								
							}
							
						} // Il n'est pas dans le tableau de clairvoyance
						else if(distPlusProche>10){ //On a le temps de se soigner
							//arene.lanceClairvoyance(refRMI, refCible);
							console.setPhrase("Je me soigne...");
							arene.lanceAutoSoin(refRMI);
						}
						else{// On suppose que le perso est plus fort
							fuite(arene,refRMI,refCible); // A l'oppose
						}
						
					}

				}
				
			}/********** FIN DU DANGER(VIE FAIBLE) **************/
			
			else { // si voisins, mais plus eloignes
				// je vais vers le plus proche
				
				//voisin le plus proche sans clairvoyance
				int refCibleTmp=voisinJoueur(arene,voisins);
				if(refCibleTmp!=0 && distPlusProche>=7){
					console.setPhrase("J'analyse "+elemPlusProche);
					tabClairvoyance[refCibleTmp]= arene.lanceClairvoyance(refRMI, refCibleTmp);
					
				}else if(!arene.estPotionFromRef(refCible) && voisinFaible(arene,refRMI,refCible)){
					
					strategieDeplacement(distPlusProche, arene, refRMI, refCible);
					
				}else if(arene.estPotionFromRef(refCible) && !potionsMauvaises.contains(refCible) && verifierPotion(arene, refCible)){
					if(distPlusProche <= Constantes.DISTANCE_MIN_INTERACTION){
						interagit(arene, refCible, refRMI, elemPlusProche);		
					}
					else{
						console.setPhrase("Je me dirige vers "+elemPlusProche);
						arene.deplace(refRMI, refCible);
					}
					
				}else if(!voisinFaible(arene,refRMI,refCible)){
					console.setPhrase("Je fuis");
					fuite(arene,refRMI,refCible);
				}
				else{
					if(perso.getCaract(Caracteristique.VIE)<100){
						console.setPhrase("Je me soigne !");
						arene.lanceAutoSoin(refRMI);
					}
					else{
						console.setPhrase("J'erre");
						arene.deplace(refRMI, 0);
					}
				}
			}
		}
	}

	
	public void fuite(IArene arene, int refRMI, int refCible) throws RemoteException{
		Point coord = arene.getPosition(refCible);
		Point coordPerso=arene.getPosition(refRMI);
		if(coordPerso.getY()==Calculs.getOffset()){
			if(coord.getX()==coord.getX())
				arene.deplace(refRMI, new Point((int)coordPerso.getX(),(int)coordPerso.getY()+1));
			if(coordPerso.getX()==Calculs.getOffset())
				arene.deplace(refRMI, new Point((int)coordPerso.getX()+1,(int)coordPerso.getY()));
			else
				arene.deplace(refRMI, new Point((int)coordPerso.getX()-1,(int)coordPerso.getY()));
		}else if(coordPerso.getY()==Constantes.YMAX_ARENE-Calculs.getOffset()){
			if(coord.getX()==coord.getX())
				arene.deplace(refRMI, new Point((int)coordPerso.getX(),(int)coordPerso.getY()-1));
			if(coordPerso.getX()==Calculs.getOffset())
				arene.deplace(refRMI, new Point((int)coordPerso.getX()+1,(int)coordPerso.getY()));
			else
				arene.deplace(refRMI, new Point((int)coordPerso.getX()-1,(int)coordPerso.getY()));
		}else if(coord.getX()>coordPerso.getX()){
			if(coord.getY()>coordPerso.getY())
				arene.deplace(refRMI, new Point((int)coordPerso.getX()-1,(int)coordPerso.getY()-1));
			else if(coord.getY()<coordPerso.getY())
				arene.deplace(refRMI, new Point((int)coordPerso.getX()-1,(int)coordPerso.getY()+1));
			else if(coordPerso.getY()>=50)
				arene.deplace(refRMI, new Point((int)coordPerso.getX()-1,(int)coordPerso.getY()-1));
			else
				arene.deplace(refRMI, new Point((int)coordPerso.getX()-1,(int)coordPerso.getY()+1));
		}else if(coord.getX()<coordPerso.getX()){
		if(coord.getY()>coordPerso.getY())
			arene.deplace(refRMI, new Point((int)coordPerso.getX()+1,(int)coordPerso.getY()-1));
		else if(coord.getY()<coordPerso.getY())
			arene.deplace(refRMI, new Point((int)coordPerso.getX()+1,(int)coordPerso.getY()+1));
		else if(coordPerso.getY()>=50)
			arene.deplace(refRMI, new Point((int)coordPerso.getX()+1,(int)coordPerso.getY()-1));
		else
			arene.deplace(refRMI, new Point((int)coordPerso.getX()+1,(int)coordPerso.getY()+1));
		}else if(coordPerso.getX()>=50){
			if(coord.getY()>coordPerso.getY())
				arene.deplace(refRMI, new Point((int)coordPerso.getX()-1,(int)coordPerso.getY()-1));
			else if(coord.getY()<coordPerso.getY())
				arene.deplace(refRMI, new Point((int)coordPerso.getX()-1,(int)coordPerso.getY()+1));
			else if(coordPerso.getY()>=50)
				arene.deplace(refRMI, new Point((int)coordPerso.getX()-1,(int)coordPerso.getY()-1));
			else
				arene.deplace(refRMI, new Point((int)coordPerso.getX()-1,(int)coordPerso.getY()+1));
		}else{
			if(coord.getY()>coordPerso.getY())
				arene.deplace(refRMI, new Point((int)coordPerso.getX()+1,(int)coordPerso.getY()-1));
			else if(coord.getY()<coordPerso.getY())
				arene.deplace(refRMI, new Point((int)coordPerso.getX()+1,(int)coordPerso.getY()+1));
			else if(coordPerso.getY()>=50)
				arene.deplace(refRMI, new Point((int)coordPerso.getX()+1,(int)coordPerso.getY()-1));
			else
				arene.deplace(refRMI, new Point((int)coordPerso.getX()+1,(int)coordPerso.getY()+1));
		}
	}
	
	
/**
 * 
 * @param refRMI
 * @param refCible
 * @param voisins
 * @param arene
 * @return si tout les voisins sont assez loin
 * @throws RemoteException
 */
public boolean verifieVoisin(int refRMI, int refCible, HashMap<Integer, Point> voisins, IArene arene ) throws RemoteException{
	
	boolean verifie = true;
	int dist; 
	
	for(int refVoisin : voisins.keySet()){
			if(refVoisin != refCible){
				dist = Calculs.distanceChebyshev( arene.getPosition(refRMI), 
						arene.getPosition(refVoisin));
				if(dist<5){
					verifie = false;
				}
				
			}
	}
	
	return verifie;
	
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
	String elem = arene.nomFromRef(cible);
	
	int dist = Calculs.distanceChebyshev( arene.getPosition(refRMI), 
			arene.getPosition(cible));
	
	if(distance == 3){
		console.setPhrase("J'attaque !");
		arene.deplace(refRMI, cible);
		arene.lanceAttaque(refRMI, cible);
	}
	else if(distance >=4 && distance<=6){
		if(arene.caractFromRef(refRMI, Caracteristique.VIE) <= VIEFAIBLE ){
			console.setPhrase("Je me soigne...");
			arene.lanceAutoSoin(refRMI);
			
		}else{
			console.setPhrase("J'analyse  " + elem);
			tabClairvoyance[cible]=arene.lanceClairvoyance(refRMI, cible);
		}
		
	}
	else{
		console.setPhrase("Je vais vers mon voisin " + elem);
		arene.deplace(refRMI, cible);
	}
	
}

	/**
	 * Cherche la potion qui rend de la vie la plus proche
	 * @param voisins tableau des voisins du personnage
	 * @param perso Personnage qui voit les elements
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
 * Permet d'interagir avec les differents elements
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

	//Retourne la reference du voisin non analyse le plus proche. 0 si aucun 
	private int voisinJoueur(IArene arene, HashMap<Integer, Point> voisins) {
		// TODO Auto-generated method stub
		for(int refVoisin : voisins.keySet()){
			try {
				if (arene.estPersonnageFromRef(refVoisin)){
					if (tabClairvoyance[refVoisin]==null)
						return refVoisin;
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return 0;
	}
	
	/**
	 * Test si le voisin est plus faible ou plus fort que nous
	 * @param arene
	 * @param refRMI
	 * @param refAdv
	 * @return true voisin faible, false sinon
	 */
	private boolean voisinFaible(IArene arene, int refRMI, int refAdv){
		if(tabClairvoyance[refAdv]!=null)
			try {
				//faible si on le OHKO ou si on le 2HKO mais pas lui 
				return ((arene.caractFromRef(refAdv, Caracteristique.VIE)<=perso.getCaract(Caracteristique.FORCE)-((tabClairvoyance[refAdv].get(Caracteristique.DEFENSE)/100)*perso.getCaract(Caracteristique.FORCE)))
				|| ((2*arene.caractFromRef(refAdv, Caracteristique.VIE)<=perso.getCaract(Caracteristique.FORCE)-((tabClairvoyance[refAdv].get(Caracteristique.DEFENSE)/100)*perso.getCaract(Caracteristique.FORCE))) 
				&& (tabClairvoyance[refAdv].get(Caracteristique.FORCE)-((perso.getCaract(Caracteristique.DEFENSE)/100)*tabClairvoyance[refAdv].get(Caracteristique.FORCE))<2*perso.getCaract(Caracteristique.VIE))));
			} catch (RemoteException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		else
			try {
				return arene.caractFromRef(refAdv, Caracteristique.VIE)<=perso.getCaract(Caracteristique.FORCE)/2;
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		return false;
	}
}
