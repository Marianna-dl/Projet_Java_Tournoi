package lanceur;

public class LanceJeu {

	public static void main(String[] args) {
		// TODO Auto-generated method stub


		LanceArene.main(args);
		LanceIHM.main(args);
		
		LancePersonnage.main(args);
		
		for(int i = 0; i<3; i++){
			LanceGuerrier.main(args);
		}
		
		for(int i = 0; i<10; i++){
			LanceMonstre.main(args);
		}
		
		
		for(int i=0; i<4; i++){

			LancePotion.main(args);	
			LancePotionTP.main(args);
		}


		
		
		
	}

}