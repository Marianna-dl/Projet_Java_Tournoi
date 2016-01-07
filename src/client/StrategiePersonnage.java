package client;


import java.awt.Point;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;

import client.controle.Console;
import logger.LoggerProjet;
import serveur.IArene;
import serveur.element.Caracteristique;
import serveur.element.Personnage;
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
		potionsMauvaises=new ArrayList<Integer>();
		tabClairvoyance=new HashMap[99];
		for(int i=0;i<99;++i)
			tabClairvoyance[i]=null;
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
		
		if (voisins.isEmpty()) { // je n'ai pas de voisins, j'erre
			console.setPhrase("J'erre...");
			arene.deplace(refRMI, 0); 
			
		} else {
			int refCible = Calculs.chercheElementProche(position, voisins);
			int distPlusProche = Calculs.distanceChebyshev(position, arene.getPosition(refCible));

			String elemPlusProche = arene.nomFromRef(refCible);
			//tabClairvoyance[refCible]=arene.lanceClairvoyance(refRMI,refCible);
			if(distPlusProche <= Constantes.DISTANCE_MIN_INTERACTION) { // si suffisamment proches
				// j'interagis directement
				if(arene.estPotionFromRef(refCible)){ // potion
					// ramassage
					console.setPhrase("Je ramasse une potion");

					//arene.ramassePotion(refRMI, refCible);			
				} else { // personnage
					// duel
					console.setPhrase("Je fais un duel avec " + elemPlusProche);
					arene.lanceAttaque(refRMI, refCible);
					arene.deplace(refRMI, refCible);
				}
				
			} else { // si voisins, mais plus eloignes
				// je vais vers le plus proche
				
				//voisin le plus proche sans clairvoyance
				int refCibleTmp=voisinJoueur(arene,voisins);
				if(refCibleTmp!=0 && distPlusProche>=7){
					arene.lanceClairvoyance(refRMI, refCibleTmp);
				}else if(!arene.estPotionFromRef(refCible) && voisinFaible(arene,refRMI,refCible)){
					strategieDeplacement(distPlusProche, arene, refRMI, refCible);
				}else if(verifierPotion(arene, refCible))
					interagit(arene, refCible, refRMI, elemPlusProche);
				else if(!voisinFaible(arene,refRMI,refCible)){
					Point coord=arene.getPosition(refCible);
					coord.x=-coord.x;
					coord.y=-coord.y;
					arene.deplace(refRMI,coord);
				}
				else{
					if(perso.getCaract(Caracteristique.VIE)<100)
						arene.lanceAutoSoin(refRMI);
					else
						arene.deplace(refRMI, 0);
				}
			}
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
		
		if(distance == 3){
			arene.deplace(refRMI, cible);
			arene.lanceAttaque(refRMI, cible);
		}
		else if(distance >=4 && distance<=6){
			//Vie faible = 40
			if(arene.caractFromRef(refRMI, Caracteristique.VIE) <= 40 ){
				arene.lanceAutoSoin(refRMI);
				
			}else{
				tabClairvoyance[cible]=arene.lanceClairvoyance(refRMI, cible);
			}
			
		}
		
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
				//Si le droit de se déplacer puis de se déplacer
				console.setPhrase("J'erre..."+ refRMI);
				//arene.deplace(refRMI, 0); 
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
		
		if(vie>=0 && force >= 0 && initiative >= 0  && defense>=0){

			return true;
			
		}	
		
		return false;
		
	}

	//Retourne la référence du voisin non analysé le plus proche. 0 si aucun 
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
	
	private boolean voisinFaible(IArene arene, int refRMI, int refAdv){
		if(tabClairvoyance[refAdv]!=null)
			try {
				if(tabClairvoyance[refAdv].get(Caracteristique.INITIATIVE)<=perso.getCaract(Caracteristique.INITIATIVE))
				//faible si on le OHKO ou si on le 2HKO mais pas lui 
					return ((arene.caractFromRef(refAdv, Caracteristique.VIE)<=perso.getCaract(Caracteristique.FORCE)-((tabClairvoyance[refAdv].get(Caracteristique.DEFENSE)/100)*perso.getCaract(Caracteristique.FORCE)))
						|| ((2*arene.caractFromRef(refAdv, Caracteristique.VIE)<=perso.getCaract(Caracteristique.FORCE)-((tabClairvoyance[refAdv].get(Caracteristique.DEFENSE)/100)*perso.getCaract(Caracteristique.FORCE))) && (tabClairvoyance[refAdv].get(Caracteristique.FORCE)-((perso.getCaract(Caracteristique.DEFENSE)/100)*tabClairvoyance[refAdv].get(Caracteristique.FORCE))<2*perso.getCaract(Caracteristique.VIE))));
				else
					return false;
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
