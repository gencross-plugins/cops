package com.mrprez.gencross.impl.cops;

import java.util.StringJoiner;

import com.mrprez.gencross.Personnage;
import com.mrprez.gencross.Property;
import com.mrprez.gencross.history.ProportionalHistoryFactory;
import com.mrprez.gencross.value.IntValue;
import com.mrprez.gencross.value.Value;

public class Cops extends Personnage {
	private final static String[] BASE_SOCIAL_COMPETENCES = new String[]{"Éloquence", "Intimidation", "Rhétorique"};
	private final static String[] BASE_CAC_SPE = new String[]{"coups", "projections", "immobilisations"};
	private final static String STAGE_REQUIREMENT_PREFIX = "Prérequis du stage ";
	
	
	public void changeCompetences(Property competence, Value oldValue){
		if(competence.getSubProperties()!=null){
			competence.getSubProperties().setFixe(competence.getSubProperties().getDefaultProperty().getValue().getInt() != competence.getValue().getInt());
		}
	}
	
	public boolean checkCompetences(Property competence, Value newValue){
		if(competence.getSubProperties()!=null){
			if(competence.getSubProperties().size()>0){
				setActionMessage("Vous ne pouvez modifier une compétence avec une ou plusieurs spécialités");
				return false;
			}
		}
		return true;
	}
	
	@Override
	public boolean phaseFinished(){
		calculate();
		for(String error : errors){
			if (!error.startsWith(STAGE_REQUIREMENT_PREFIX)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void calculate() {
		super.calculate();
		calculateLangues();
		calculateTirRafale();
		calculateStageRequirement();
		if(!getPhase().equals("En service")){
			calculateCaracteristiques();
			limiteStageLevel();
		}
		if(getPhase().equals("Compétences de bases")){
			calculateBaseCompetences();
		}
		if(getPhase().equals("Compétences")){
			calculateCompetences();
		}
		if(getPhase().equals("Origine sociale")){
			if(getProperty("Equipement").getSubProperties().size()!=1){
				errors.add("Vous avez le droit à un et un seul équipement supplémentaire");
			}
		}
		if(getPhase().equals("Relations supplémentaires")){
			calculateRelation();
		}
	}
	
	private void calculateStageRequirement() {
		for(Property stage : getProperty("Stages").getSubProperties()){
			for(String requirement : appendix.getSubMap("requirement."+stage.getName()).values()){
				StringJoiner errorList = new StringJoiner(" ou ");
				for (String requirementClause : requirement.split("[|]")) {
					String errorMsg = calculateOneRequirement(requirementClause);
					if (errorMsg != null) {
						errorList.add(errorMsg);
					}
				}
				if (errorList.length() > 0) {
					errors.add(STAGE_REQUIREMENT_PREFIX + stage.getName() + " :" + errorList);
				}
			}
			if (stage.getName().equals("Communication médiatique Niveau 3")) {
				calculateMassGuruStageRequirement();
			}
			if (stage.getName().equals("Contrôle des biens et des contrefaçons Niveau 2")) {
				calculateReceleurStageRequirement(stage);
			}
			if (stage.getName().startsWith("Manœuvres et mouvements Niv. 2")) {
				calculateBulldozerStageRequirement();
			}
			if (stage.getName().startsWith("Négociation Niv. 3")) {
				// TODO
			}
		}
	}

	private void calculateMassGuruStageRequirement() {
		Property ocobRelation = null;
		for (Property relation : getProperty("Relations").getSubProperties()) {
			if (relation.getName().toUpperCase().contains("OCOB")) {
				if (ocobRelation == null || ocobRelation.getValue().getInt() < relation.getValue().getInt()) {
					ocobRelation = relation;
				}
			}
		}
		if (ocobRelation == null || ocobRelation.getValue().getInt() < 2) {
			errors.add(STAGE_REQUIREMENT_PREFIX + "Communication médiatique Niveau 3: Relation au sein de l’OCOB de niveau: 2");
		}
	}

	private void calculateReceleurStageRequirement(Property stage) {
		Property connaissance = getProperty("Compétences#Connaissance#" + stage.getSpecification());
		if (connaissance == null || connaissance.getValue().getInt() > 6) {
			errors.add(STAGE_REQUIREMENT_PREFIX + stage.getFullName() + ": Connaissance/" + stage.getSpecification() + ": 6");
		}
	}

	private void calculateBulldozerStageRequirement() {
		int level1StageNb = 0;
		for (Property stage : getProperty("Stages").getSubProperties()) {
			if (stage.getName().startsWith("Manœuvres et mouvements Niv. 1")) {
				level1StageNb++;
			}
		}
		if (level1StageNb < 2) {
			errors.add(STAGE_REQUIREMENT_PREFIX + "Manœuvres et mouvements Niv. 1: Stage niveau 1 (x2: minimum)");
		}
	}

	private String calculateOneRequirement(String requirement) {
		if (requirement.contains(":")) {
			String propertyName = requirement.split(":")[0];
			int limit = Integer.parseInt(requirement.split(":")[1]);
			Property property = getProperty(propertyName);
			if (propertyName.startsWith("Compétences#")) {
				if (getProperty(requirement) == null || property.getValue().getInt() > limit) {
					return property.getName() + ": " + limit;
				}
			} else {
				if (getProperty(requirement) == null || property.getValue().getInt() < limit) {
					return property.getName() + ": " + limit;
				}
			}
		} else {
			if (getProperty(requirement) == null) {
				return requirement;
			}
		}
		return null;
	}


	private void limiteStageLevel() {
		for(Property stage : getProperty("Stages").getSubProperties()){
			if(stage.getName().charAt(stage.getName().length()-1)!='1'){
				errors.add("A la création, vous ne pouvez pas avoir de stage de niveau 2 ou 3");
			}
		}
	}

	private void calculateTirRafale() {
		for(Property specialite : getProperty("Compétences#Tir en Rafale").getSubProperties()){
			Property tirComp = getProperty("Compétences").getSubProperty(specialite.getName());
			int tirLevel = tirComp.getValue().getInt();
			if(tirComp.getSubProperties()!=null){
				for(Property tirSpe : tirComp.getSubProperties()){
					if(tirSpe.getValue().getInt()<tirLevel){
						tirLevel = tirSpe.getValue().getInt();
					}
				}
			}
			if(specialite.getValue().getInt()<tirLevel){
				errors.add("Votre score de Tir en Rafale / "+specialite.getName()+" ne peut être inférieur à votre score en "+specialite.getName());
			}
		}
		
	}

	private void calculateLangues(){
		if(getProperty("Caracteristiques#Education").getValue().getInt() != getProperty("Langues").getSubProperties().size()){
			errors.add("Vous devez avoir autant de Langues que votre Caractéristique d'Education");
		}
	}
	
	private void calculateCaracteristiques(){
		int caracAt5Nb = 0;
		for(Property carac : getProperty("Caracteristiques").getSubProperties()){
			if(carac.getValue().getInt()>=5){
				caracAt5Nb++;
			}
		}
		if(caracAt5Nb>1){
			errors.add("Vous ne pouvez avoir plus d'une Caractéristique à 5 à la création");
		}
	}
	
	private void calculateRelation(){
		int relationOver4Nb = 0;
		for(Property relation : getProperty("Relations").getSubProperties()){
			if(relation.getValue().getInt()>=4){
				relationOver4Nb++;
			}
		}
		if(relationOver4Nb>2){
			errors.add("Vous ne pouvez avoir plus de 2 relations au niveau 4");
		}
	}
	
	private void calculateCompetences(){
		int baseCompetenceCount = 0;
		for(Property competence : getProperty("Compétences").getSubProperties()){
			if(competence.getValue()!=null){
				if(competence.getMax().getInt()<10){
					baseCompetenceCount = baseCompetenceCount + competence.getMax().getInt() - competence.getValue().getInt();
				}
			}
			if(competence.getSubProperties()!=null){
				for(Property specialite : competence.getSubProperties()){
					if(specialite.getMax().getInt()<10){
						baseCompetenceCount = baseCompetenceCount + specialite.getMax().getInt() - specialite.getValue().getInt();
					}
				}
			}
		}
		if(baseCompetenceCount>5){
			errors.add("Vous ne pouvez dépenser plus de 5 points dans les compétences de bases");
		}
	}
	
	private void calculateBaseCompetences(){
		int socialCompCount = 0;
		int social7CompCount = 0;
		for(String competenceName : BASE_SOCIAL_COMPETENCES){
			Property competence = getProperty("Compétences").getSubProperty(competenceName);
			if(competence.getValue().getInt()<10){
				socialCompCount++;
			}
			if(competence.getValue().getInt()==7){
				social7CompCount++;
			}
		}
		if(socialCompCount!=1 || social7CompCount!=1){
			getErrors().add("Vous devez positionner une et une seule des compétences suivantes à 7: Éloquence, Intimidation ou Rhétorique");
		}
		
		int cac7Count = 0;
		for(String specialite : BASE_CAC_SPE){
			if(getProperty("Compétences#Corps à Corps").getSubProperty(specialite)!=null && getProperty("Compétences#Corps à Corps").getSubProperty(specialite).getValue().getInt()==7){
				cac7Count++;
			}
		}
		if(cac7Count!=1 || getProperty("Compétences#Corps à Corps").getSubProperties().size()!=1){
			getErrors().add("Vous devez positionner une et une seule des spécialités de Corps à Corps suivantes à 7: coups, projections ou immobilisations");
		}
	}
	
	public void goToPhaseCompetence(){
		// Compétences sociales
		for(String competenceName : BASE_SOCIAL_COMPETENCES){
			Property competence = getProperty("Compétences").getSubProperty(competenceName);
			competence.setMax();
			competence.setMin(new IntValue(competence.getValue().getInt()-2));
		}
		
		// Spécialités de corps à corps
		Property cacCompetence = getProperty("Compétences#Corps à Corps");
		for(Property cacSpe : cacCompetence.getSubProperties()){
			cacSpe.setMax();
			cacSpe.setMin(new IntValue(cacSpe.getValue().getInt()-2));
		}
		for(Property cacSpe : cacCompetence.getSubProperties().getOptions().values()){
			cacSpe.setMax();
			cacSpe.setMin(new IntValue(cacSpe.getValue().getInt()-2));
		}
		Property cacSpe = cacCompetence.getSubProperties().getDefaultProperty();
		cacSpe.setMin(new IntValue(cacSpe.getValue().getInt()-2));
		
		// Set competences editable
		for(Property competence : getProperty("Compétences").getSubProperties()){
			if(competence.getValue()!=null){
				competence.setEditable(true);
				if(competence.getSubProperties()!=null && competence.getValue()!=null && competence.getValue().equals(competence.getSubProperties().getDefaultProperty().getValue())){
					competence.setEditable(false);
					competence.getSubProperties().setFixe(false);
				}
			}else{
				competence.getSubProperties().setFixe(false);
			}
		}
		
		// Competence history factory
		getProperty("Compétences").setHistoryFactory(new ProportionalHistoryFactory("Compétences", -1));
		for(Property competence : getProperty("Compétences").getSubProperties()){
			if(competence.getSubProperties()!=null){
				int startValue = competence.getSubProperties().getDefaultProperty().getValue().getInt();
				competence.getSubProperties().getDefaultProperty().setHistoryFactory(new ProportionalHistoryFactory("Compétences", -1, startValue));
				for(Property spe : competence.getSubProperties()){
					spe.setHistoryFactory(new ProportionalHistoryFactory("Compétences", -1, startValue));
				}
				for(Property spe : competence.getSubProperties().getOptions().values()){
					spe.setHistoryFactory(new ProportionalHistoryFactory("Compétences", -1, startValue));
				}
			}
		}
		getPointPools().get("Compétences").setToEmpty(true);
	}
	
	public void goToPhaseOrigineSociale(){
		getPointPools().get("Relations").add(2);
	}
	
	public void goToPhaseEtude(){
		for(Property competence : getProperty("Compétences").getSubProperties()){
			if(competence.getValue()!=null){
				competence.setMax();
				competence.setMin(new IntValue(2));
			}
			if(competence.getSubProperties()!=null){
				for(Property specialite : competence.getSubProperties()){
					specialite.setMax();
					specialite.setMin(new IntValue(2));
				}
				competence.getSubProperties().getDefaultProperty().setMin(new IntValue(2));
				for(Property specialite : competence.getSubProperties().getOptions().values()){
					specialite.setMin(new IntValue(2));
				}
			}
		}
		getPointPools().get("Compétences").add(2);
		getPointPools().get("Relations").add(1);
	}
	
	public void goToPhaseDevenirCops(){
		getPointPools().get("Relations").add(2);
		getPointPools().get("Adrénaline/Ancienneté").setToEmpty(true);
	}
	
	public void goToPhaseRelationSupplementaire(){
		getPointPools().get("Relations").add(2);
	}
	
	public void goToPhaseStage(){
		getPointPools().get("Stages").setToEmpty(true);
	}
	
}
