package com.teleonome.fertilizer;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.log4j.Category;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;  
import org.json.JSONException;
import org.json.JSONObject;

import com.teleonome.fertilizer.utils.FertilizationUtils;
import com.teleonome.framework.TeleonomeConstants;
import com.teleonome.framework.denome.DenomeManager;
import com.teleonome.framework.denome.DenomeUtils;
import com.teleonome.framework.denome.DenomeValidator;
import com.teleonome.framework.denome.Identity;
import com.teleonome.framework.exception.InvalidDenomeException;
import com.teleonome.framework.exception.MissingDenomeException;
import com.teleonome.framework.exception.TeleonomeValidationException;
import com.teleonome.framework.mnemosyne.MnemosyneManager;
import com.teleonome.framework.persistence.PostgresqlPersistenceManager;
import com.teleonome.framework.utils.Utils;



public class Fertilizer {
	static Logger logger;
	private PostgresqlPersistenceManager aDBManager=null;
	String denomeFileInString;
	private JSONObject pulseJSONObject;
	private String buildNumber="06/05/2018 07:55";
	private static String spermFileName;
	private static String eggTeleonomeLocation;
	static String dataDirectory = Utils.getLocalDirectory() + "avocado/";
	
	public Fertilizer(){

		logger =  Logger.getLogger(getClass());

		SimpleDateFormat simpleFormatter = new SimpleDateFormat("dd/MM/yy HH:mm");
		Calendar cal = Calendar.getInstance();//TimeZone.getTimeZone("GMT+10:00"));

		String processName = ManagementFactory.getRuntimeMXBean().getName();
		try {
			FileUtils.writeStringToFile(new File("Fertilizer.info"), processName);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}


		logger.warn("Fertilizer build " + buildNumber + " Process Number:" + processName);  
		//
		//  stop the Hypothalamus
		//
		boolean continueProcess=false;

		File file = new File("PaceMakerProcess.info");
		if(file.isFile()) {
			try {
				int hypothalamusPid = Integer.parseInt(FileUtils.readFileToString(file).split("@")[0]);
				ArrayList results = Utils.executeCommand("ps -p " + hypothalamusPid);
				//
				// if the pacemaker is running it will return two lines like:
				//PID TTY          TIME CMD
				//1872 pts/0    00:02:45 java
				//if it only returns one line then the process is not running

				if(results.size()<2){
					logger.info("pacemaker is not running");

				}else{
					logger.info("pacemaker is  running, killing it...");
					Utils.executeCommand("sudo kill -9  " + hypothalamusPid);
				}
				continueProcess=true;
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				logger.warn(Utils.getStringException(e1));
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				logger.warn(Utils.getStringException(e));
			} catch (NumberFormatException e3) {
				// TODO Auto-generated catch block
				e3.printStackTrace();
			}

		}else {
			//
			// if we are here is most likely diring the creation process when the hypothalamus has
			// never ran
			continueProcess=true;
		}






		if(!continueProcess) {
			logger.info("There was a problem stopping the Hypothalamus, stopping the fertilization");
			System.exit(-1);
		}
		//
		// if we are here, then the hypothalamus has stopped
		// so  move the existing Teleonome to a new directory

		moveFiles();

		File selectedFile = new File( eggTeleonomeLocation);
		logger.debug("reading egg from " + eggTeleonomeLocation);

		try {
			denomeFileInString = FileUtils.readFileToString(selectedFile);
			pulseJSONObject = new JSONObject(denomeFileInString);


			//
			// now read the sperm
			String spermFileInString = FileUtils.readFileToString(new File(dataDirectory + spermFileName));
			JSONObject completeSpermJSONObject = new JSONObject(spermFileInString);
			//
			// verify the integrity and consistency of the sperm
			//				String verificationResult = FertilizationUtils.verifySperm(pulseJSONObject, completeSpermJSONObject);
			//				if(!verificationResult.equals(TeleonomeConstants.SPERM_VALIDATED)){
			//					System.out.println ("The sperm is invalid, will stop now.  Here are the problems:" +  System.lineSeparator() + verificationResult);
			//					System.exit(-1);
			//				}



			/*
			 * the two lines below that remome the virtual controller are commented out now
			 * in my original idea,  i assumed that there would always be a controller
			 * that will deal with the two required actions of setnetworkmode and set hostmode
			 * but creating tlaloc i came across the case where i really dont need a m
			 * 
				//
				//remove @Egg:Internal:Components:Simple Micro Controller
				//
				DenomeUtils.removeDeneFromChain(pulseJSONObject, TeleonomeConstants.NUCLEI_INTERNAL, TeleonomeConstants.DENECHAIN_COMPONENTS, TeleonomeConstants.EGG_VIRTUAL_MICROCONTROLLER);
				//
				//remove @Egg:Casetera:Internal:Actuators:VirtualActuator
				//
				DenomeUtils.remveCodonFromDeneChain(pulseJSONObject, TeleonomeConstants.NUCLEI_INTERNAL, TeleonomeConstants.DENECHAIN_ACTUATORS, TeleonomeConstants.EGG_VIRTUAL_ACTUATOR);

			 */


			//
			// get the name of the new teleonome
			JSONObject spermJSONObject = completeSpermJSONObject.getJSONObject(TeleonomeConstants.SPERM);
			JSONObject purposeJSONObject = spermJSONObject.getJSONObject(TeleonomeConstants.SPERM_PURPOSE);

			String purposeType = purposeJSONObject.getString(TeleonomeConstants.SPERM_PURPOSE_TYPE);
			//
			// there are two potential values to purposeType, create and mutate.
			// create means you are starting with an egg
			// mutate means you already have the teleonome but you are adding new denomic structures
			//
			JSONObject denomeJSONObject = pulseJSONObject.getJSONObject("Denome");

			if(purposeType.equals(TeleonomeConstants.SPERM_PURPOSE_TYPE_CREATE)) {
				String newTeleonomeName = purposeJSONObject.getString(TeleonomeConstants.SPERM_PURPOSE_NAME);
				String newTeleonomeDescription = purposeJSONObject.getString(TeleonomeConstants.SPERM_PURPOSE_DESCRIPTION);
				//
				// the next step is change the name from Egg to newTeleonomeName
				//

				denomeJSONObject.put("Name", newTeleonomeName);
				denomeJSONObject.put("Description", newTeleonomeDescription);

				//
				// the next step is to replace all references to @Egg for @newTeleonomeName
				// this will be easier by converting the JSN back to a string, do replace and the recreate the JSON
				//
				String teleonomeBackInString = pulseJSONObject.toString();
				String teleonomeUpdatedReferences = teleonomeBackInString.replace("@NewEgg:", "@" + newTeleonomeName + ":");
				pulseJSONObject = new JSONObject(teleonomeUpdatedReferences);
				//
				// refresh the denomeobject
				//
				denomeJSONObject = pulseJSONObject.getJSONObject("Denome");
			}


			//
			// now read the homeboxes 
			JSONObject pacemakerJSONObject = spermJSONObject.getJSONObject(TeleonomeConstants.SPERM_HYPOTHALAMUS);
			JSONArray homeboxesJSONArray = pacemakerJSONObject.getJSONArray(TeleonomeConstants.SPERM_HYPOTHALAMUS_HOMEOBOXES);
			JSONArray actionsJSONArray = pacemakerJSONObject.getJSONArray(TeleonomeConstants.SPERM_HYPOTHALAMUS_ACTIONS);
			JSONArray mutationsJSONArray = pacemakerJSONObject.getJSONArray(TeleonomeConstants.SPERM_HYPOTHALAMUS_MUTATIONS);

			JSONObject actionJSONObject;
			JSONObject homeoboxJSONObject;

			JSONArray homeoboxDenes;
			JSONObject homeoboxDene;
			ArrayList allDenePointersForHomebox;
			String denePointer;
			FertilizationIdentity fertilizationIdentity;
			JSONObject hoxDene;
			String hoxDeneTargetPointer;
			Identity hoxDeneTargetIdentity;
			JSONArray homeoboxIndexDeneWords, actionDeneWords;
			JSONObject homeoboxIndexDeneWord;
			String hoxDenePointerValue;
			int executionPosition=0;
			ArrayList<Map.Entry<JSONObject, Integer>>  preHomeBoxActionEvaluationPositionActionIndex = new ArrayList(); 
			ArrayList<Map.Entry<JSONObject, Integer>>  postHomeBoxActionEvaluationPositionActionIndex = new ArrayList(); 
			String executionPoint, actionTargetPointer;
			for(int i=0;i<actionsJSONArray.length();i++){
				actionJSONObject = actionsJSONArray.getJSONObject(i);
				executionPoint = (String)DenomeUtils.getDeneWordAttributeByDeneWordNameFromDene( actionJSONObject, TeleonomeConstants.SPERM_ACTION_DENEWORD_EXECUTION_POINT, TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);  
				if(executionPoint!=null) {
					executionPosition = (Integer)DenomeUtils.getDeneWordAttributeByDeneWordNameFromDene( actionJSONObject, TeleonomeConstants.SPERM_ACTION_DENEWORD_EXECUTION_POSITION, TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);  
					if(executionPoint.equals(TeleonomeConstants.SPERM_ACTION_DENEWORD_EXECUTION_POINT_PRE_HOMEBOX)) {
						preHomeBoxActionEvaluationPositionActionIndex.add(new AbstractMap.SimpleEntry<JSONObject, Integer>(actionJSONObject, new Integer(executionPosition)));
					}else if(executionPoint.equals(TeleonomeConstants.SPERM_ACTION_DENEWORD_EXECUTION_POINT_POST_HOMEBOX)) {
						postHomeBoxActionEvaluationPositionActionIndex.add(new AbstractMap.SimpleEntry<JSONObject, Integer>(actionJSONObject, new Integer(executionPosition)));
					}	
				}
			}

			Collections.sort(preHomeBoxActionEvaluationPositionActionIndex, new IntegerCompare());
			Collections.sort(postHomeBoxActionEvaluationPositionActionIndex, new IntegerCompare());
			//
			// now do the pre homeboxes action
			String newDeneChainName, newMutationName, newMutationExecutionMode, newMutationType;
			Identity actionTargetIdentity, newDeneChainIdentity;

			for (Map.Entry<JSONObject, Integer> action : preHomeBoxActionEvaluationPositionActionIndex) {
				actionJSONObject = action.getKey();
				actionTargetPointer = actionJSONObject.getString(TeleonomeConstants.SPERM_ACTION_DENE_TARGET);
				if(actionJSONObject.has(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE) && actionJSONObject.getString(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE).equals(TeleonomeConstants.SPERM_DENE_TYPE_CREATE_DENE_CHAIN)){
					newDeneChainName = (String) DenomeUtils.getDeneWordAttributeByDeneWordNameFromDene(actionJSONObject, TeleonomeConstants.SPERM_ACTION_DENEWORD_DENECHAIN_NAME, TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);
					actionTargetIdentity = new Identity(actionTargetPointer);
					JSONObject newDeneChain = new JSONObject();
					newDeneChain.put(TeleonomeConstants.DENE_DENE_NAME_ATTRIBUTE, newDeneChainName);
					newDeneChain.put("Denes", new JSONArray());
					newDeneChainIdentity = new Identity (actionTargetPointer  + ":" + newDeneChainName);
					if(!DenomeUtils.containsDenomicElementByIdentity( pulseJSONObject, newDeneChainIdentity)) {
						DenomeUtils.addDeneChainToNucleusByIdentity( pulseJSONObject, newDeneChain,  actionTargetIdentity);
					}
				}else if(actionJSONObject.has(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE) && actionJSONObject.getString(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE).equals(TeleonomeConstants.SPERM_DENE_TYPE_CREATE_MUTATION)){
					newMutationName = (String) DenomeUtils.getDeneWordAttributeByDeneWordNameFromDene(actionJSONObject, TeleonomeConstants.SPERM_ACTION_DENEWORD_MUTATION_NAME, TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);
					logger.info("line 266 newMutationName=" + newMutationName);
					
					if(!DenomeUtils.containsMutation( pulseJSONObject, newMutationName)) {
						newMutationExecutionMode = (String) DenomeUtils.getDeneWordAttributeByDeneWordNameFromDene(actionJSONObject, "Execution Mode", TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);
						newMutationType = (String) DenomeUtils.getDeneWordAttributeByDeneWordNameFromDene(actionJSONObject, TeleonomeConstants.MUTATION_TYPE_ATTRIBUTE, TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);
						logger.info("line 271 newMutationType=" + newMutationType);
						actionTargetIdentity = new Identity(actionTargetPointer);
						JSONObject newMutation = new JSONObject();
						newMutation.put("Name", newMutationName);
						newMutation.put(TeleonomeConstants.DENEWORD_ACTIVE, true);
						newMutation.put("Execution Mode", newMutationExecutionMode);
						newMutation.put(TeleonomeConstants.MUTATION_TYPE_ATTRIBUTE, newMutationType);
						JSONArray mutationDeneChainsJSONArray = new JSONArray();
						newMutation.put("DeneChains", mutationDeneChainsJSONArray);

						JSONObject onLoad = new JSONObject();
						mutationDeneChainsJSONArray.put(onLoad);
						onLoad.put("Name", TeleonomeConstants.DENE_TYPE_ON_LOAD_MUTATION);
						onLoad.put("Denes", new JSONArray());

						JSONObject actionsToExecute = new JSONObject();
						mutationDeneChainsJSONArray.put(actionsToExecute);
						actionsToExecute.put("Name", TeleonomeConstants.DENECHAIN_ACTIONS_TO_EXECUTE);
						actionsToExecute.put("Denes", new JSONArray());

						JSONObject mnemosyconsToExecute = new JSONObject();
						mutationDeneChainsJSONArray.put(mnemosyconsToExecute);
						mnemosyconsToExecute.put("Name", TeleonomeConstants.DENECHAIN_MNEMOSYCONS_TO_EXECUTE);
						mnemosyconsToExecute.put("Denes", new JSONArray());

						JSONObject mutationProcessingLogic = new JSONObject();
						mutationDeneChainsJSONArray.put(mutationProcessingLogic);
						mutationProcessingLogic.put("Name", TeleonomeConstants.MUTATION_PROCESSING_LOGIC_DENE_CHAIN_NAME);
						mutationProcessingLogic.put("Denes", new JSONArray());

						JSONObject mnemosyneOperations = new JSONObject();
						mutationDeneChainsJSONArray.put(mnemosyneOperations);
						mnemosyneOperations.put("Name", TeleonomeConstants.DENECHAIN_MNEMOSYNE_OPERATIONS);
						mnemosyneOperations.put("Denes", new JSONArray());


						JSONObject mutationConfiguration = new JSONObject();
						mutationDeneChainsJSONArray.put(mutationConfiguration);
						mutationConfiguration.put("Name", "Mutation Configuration");
						mutationConfiguration.put("Denes", new JSONArray());
					
						JSONObject deneWordInjection = new JSONObject();
						mutationDeneChainsJSONArray.put(deneWordInjection);
						deneWordInjection.put("Name", TeleonomeConstants.DENECHAIN_DENEWORD_INJECTION);
						JSONArray denesArray = new JSONArray();
						deneWordInjection.put("Denes",denesArray);
						JSONObject deneCarrierDene = new JSONObject();
						denesArray.put(deneCarrierDene);
						deneCarrierDene.put(TeleonomeConstants.DENE_DENE_NAME_ATTRIBUTE, TeleonomeConstants.DENE_TYPE_DENEWORD_CARRIER);
						deneCarrierDene.put("DeneWords", new JSONArray());
						deneCarrierDene.put(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE, TeleonomeConstants.DENE_TYPE_DENEWORD_CARRIER);
						
						JSONObject deneInjection = new JSONObject();
						mutationDeneChainsJSONArray.put(deneInjection);
						deneInjection.put("Name", TeleonomeConstants.DENECHAIN_DENE_INJECTION);
						deneInjection.put("Denes", new JSONArray());
						
						JSONObject onFinish = new JSONObject();
						mutationDeneChainsJSONArray.put(onFinish);
						onFinish.put("Name", TeleonomeConstants.DENE_TYPE_ON_FINISH_MUTATION);
						onFinish.put("Denes", new JSONArray());
						
						DenomeUtils.addMutationToMutations(pulseJSONObject, newMutation);
					}else {
						
					}


				}
			}
			//
			// now add the homeboxes
			//
			Identity newDeneIdentity;
			String homeoboxDeneName;
			for(int i=0;i<homeboxesJSONArray.length();i++){
				homeoboxJSONObject = homeboxesJSONArray.getJSONObject(i);
				allDenePointersForHomebox = new ArrayList();
				homeoboxDenes = homeoboxJSONObject.getJSONArray(TeleonomeConstants.SPERM_HOMEOBOX_DENES);
				for(int j=0;j<homeoboxDenes.length();j++){
					homeoboxDene = homeoboxDenes.getJSONObject(j);
					homeoboxDeneName = homeoboxDene.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE);
					logger.debug("homeoboxDeneName name=" + homeoboxDeneName);
					if(homeoboxDene.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE).equals(TeleonomeConstants.SPERM_HOMEOBOX_INDEX)){
						//
						// we are in the correct dene to get the index of all the other denes that need to be acted upon in this homebox
						// get the deneword of type Hox Dene Pointer
						homeoboxIndexDeneWords = homeoboxDene.getJSONArray("DeneWords");
						for(int k=0;k<homeoboxIndexDeneWords.length();k++){
							homeoboxIndexDeneWord = homeoboxIndexDeneWords.getJSONObject(k);
							if(homeoboxIndexDeneWord.getString(TeleonomeConstants.DENEWORD_DENEWORD_TYPE_ATTRIBUTE).equals(TeleonomeConstants.SPERM_HOX_DENE_POINTER)){
								denePointer =  homeoboxIndexDeneWord.getString(TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);
								logger.debug("line 364 adding denePointer=" + denePointer);

								fertilizationIdentity = new FertilizationIdentity(denePointer);

								hoxDene = FertilizationUtils.getDeneBySpermIdentity( completeSpermJSONObject,  fertilizationIdentity) ;
								logger.debug("hoxDene=" + hoxDene);
								if(hoxDene==null) {
									logger.warn( "  " );
									logger.warn("The sperm is misconfigured, the homebox index " + homeoboxJSONObject.getString("Name") + " points to: " );
									logger.warn(denePointer );
									logger.warn( " which can not be found in the homebox." );
									logger.warn( "  " );
									logger.warn( " Can not continue" );
									logger.warn(" ");
									logger.warn("Reverting to previous state");
									undoFertilization();
									System.exit(-1);


								}else if(!hoxDene.has(TeleonomeConstants.SPERM_HOX_DENE_TARGET)) {
									logger.warn( "  " );
									logger.warn("The sperm is misconfigured, the hoxDene " + hoxDene.getString("Name") );
									logger.warn(denePointer );
									logger.warn( " is missing the Target attribute." );
									logger.warn( "  " );
									logger.warn( " Can not continue" );
									logger.warn(" ");
									logger.warn("Reverting to previous state");
									undoFertilization();
									System.exit(-1);								
								}

								hoxDeneTargetPointer = hoxDene.getString(TeleonomeConstants.SPERM_HOX_DENE_TARGET);
								hoxDeneTargetIdentity = new Identity(hoxDeneTargetPointer);
								//
								// the hoxDeneTargetPointer contains a pointer to where this dene needs to be moved, first removed the target from the dene
								hoxDene.remove(TeleonomeConstants.SPERM_HOX_DENE_TARGET);
								//
								// now insert the dene into the teleonome
								// there are two posibilities, the dene is going to be inserted into a 
								// nucleus:denechain or it will be inserted into a mutation
								// if its going into a nucleus:denechain then
								if(DenomeUtils.isMutationIdentity(hoxDeneTargetPointer)) {
									String mutationName=hoxDene.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE);
									newDeneIdentity = new Identity (hoxDeneTargetPointer  + ":" + mutationName);
									logger.debug("line 397 adding hoxDeneTargetIdentity=" + hoxDeneTargetIdentity + " HoxDene Name=" + hoxDene.getString("Name")  + " newDeneIdentity=" + newDeneIdentity.toString());

									if(DenomeUtils.containsMutation( pulseJSONObject, mutationName)) {
										DenomeUtils.addDeneToMutationDeneChainByIdentity( pulseJSONObject, hoxDene,  hoxDeneTargetIdentity);
									}else {
										logger.warn("Did not add " + hoxDeneTargetIdentity +":"+ hoxDene.getString("Name") + " because the mutation does not existed" );
									}
								}else {
									newDeneIdentity = new Identity (hoxDeneTargetPointer  + ":" + hoxDene.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE));
									logger.debug("line 418 adding hoxDeneTargetIdentity=" + hoxDeneTargetIdentity);
									logger.debug("line 419 HoxDene Name=" + hoxDene.getString("Name") );
									logger.debug("line 420 newDeneIdentity=" + newDeneIdentity.toString());
									if(!DenomeUtils.containsDenomicElementByIdentity( pulseJSONObject, newDeneIdentity)) {
										DenomeUtils.addDeneToDeneChainByIdentity( pulseJSONObject, hoxDene,  hoxDeneTargetIdentity);
									}else {
										logger.warn("Did not add " + hoxDeneTargetIdentity +":"+ hoxDene.getString("Name") + " because it already existed" );
										
											try {
												FileUtils.writeStringToFile(new File("Bug.denome"), pulseJSONObject.toString(4));
											} catch (IOException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
										
									}
								}
								
							}
						}
					}

					//
					//  "DeneWOrd Remover"
					// which is used to remove denewords from existing denes.  
					JSONObject homeoboxRemoverDeneWord;
					Identity targetDeneWordIdentity;
					if(homeoboxDene.has(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE) &&
							homeoboxDene.get(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE).equals(TeleonomeConstants.SPERM_DENE_TYPE_DENEWORD_REMOVER)){
						JSONArray removerDeneWords = homeoboxDene.getJSONArray("DeneWords");
						//String removerDeneTargetPointer1 = homeoboxDene.getString(TeleonomeConstants.SPERM_HOX_DENE_TARGET);
						//Identity removerDeneTargetIdentity1 = new Identity(removerDeneTargetPointer);

						for(int k=0;k<removerDeneWords.length();k++){
							homeoboxRemoverDeneWord = removerDeneWords.getJSONObject(k);
							logger.debug("line 350 homeoboxRemoverDeneWord " + homeoboxRemoverDeneWord.toString(4));

							targetDeneWordIdentity = new Identity(homeoboxRemoverDeneWord.getString(TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE));	
							logger.debug("about to remove " + targetDeneWordIdentity.toString());
							boolean removed = DenomeUtils.removeDeneWordFromDeneByIdentity( pulseJSONObject, targetDeneWordIdentity);

						}
					}

					//
					//  "DeneWOrd Carrier"
					// which is used to insert denewords into existing denes.  there could be many of this because every dene will
					// have a target which represents the dene where the denewords will be inserted.
					// The loop is run again, to make sure that all the denes are inserted first, then the denewords
					JSONObject homeoboxCarrierDeneWord;
					Identity newDeneWordIdentity;
					logger.debug("line 460 " +homeoboxDeneName + " has DENE_DENE_TYPE_ATTRIBUTE carrier=" + homeoboxDene.toString(4)) ;
					boolean hasDeneWordCarrier=homeoboxDene.has(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE);
					String deneTypeAttribute = "";

					logger.debug("line 464 " +homeoboxDeneName + " has DENE_DENE_TYPE_ATTRIBUTE carrier=" + hasDeneWordCarrier) ;
					if(hasDeneWordCarrier) {
						deneTypeAttribute = homeoboxDene.getString(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE);
						logger.debug("line 467 deneTypeAttribute=" + deneTypeAttribute) ;
					}
					if(hasDeneWordCarrier && deneTypeAttribute.equals(TeleonomeConstants.SPERM_DENE_TYPE_DENEWORD_CARRIER)){
						JSONArray carrierDeneWords = homeoboxDene.getJSONArray("DeneWords");
						String carrierDeneTargetPointer = homeoboxDene.getString(TeleonomeConstants.SPERM_HOX_DENE_TARGET);
						Identity carrierDeneTargetIdentity = new Identity(carrierDeneTargetPointer);
						logger.debug("carrierDeneWords=" + carrierDeneWords.length()) ;

						for(int k=0;k<carrierDeneWords.length();k++){
							homeoboxCarrierDeneWord = carrierDeneWords.getJSONObject(k);
							newDeneWordIdentity = new Identity (carrierDeneTargetPointer  + ":" + homeoboxCarrierDeneWord.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE));
							logger.debug("adding hoxDeneTargetIdentity=" + carrierDeneTargetIdentity.toString() + " homeoboxCarrierDeneWord Name=" + homeoboxCarrierDeneWord.getString("Name")  + " newDeneWordIdentity=" + newDeneWordIdentity.toString());
							logger.debug("Line 479 newDeneWordIdentity=" + newDeneWordIdentity.toString());
							//
							// we are adding a deneword given by identity newDeneWordIdentity
							// check to see if the dene that the deneword belongs to exist
							// this dene identity is stored in carrierDeneTargetIdentity
							if(!DenomeUtils.containsDenomicElementByIdentity( pulseJSONObject, carrierDeneTargetIdentity)) {
								JSONObject newDene = new JSONObject();
								newDene.put(TeleonomeConstants.DENE_NAME_ATTRIBUTE, carrierDeneTargetIdentity.getDeneName());
								newDene.put("DeneWords", new JSONArray());
								boolean addDeneResult = DenomeUtils.addDeneToDeneChainByIdentity(pulseJSONObject, newDene, carrierDeneTargetIdentity.getNucleusName(), carrierDeneTargetIdentity.getDenechainName());
								logger.debug("line 489 addDeneResult=" + addDeneResult);

							}
							if(!DenomeUtils.containsDenomicElementByIdentity( pulseJSONObject, newDeneWordIdentity)) {

								boolean addDeneWordResult = DenomeUtils.addDeneWordToDeneByIdentity( pulseJSONObject, homeoboxCarrierDeneWord,  carrierDeneTargetIdentity);	
								logger.debug("line 495 addDeneWordResult=" + addDeneWordResult +" carrierDeneTargetIdentity=" + carrierDeneTargetIdentity.toString());
								logger.debug("line 496 homeoboxCarrierDeneWord=" + homeoboxCarrierDeneWord.toString(4));

							}else {
								logger.warn("Did not add " + carrierDeneTargetIdentity +":"+ homeoboxCarrierDeneWord.getString("Name") + " because it already existed" );

							}
						}
					}




				}	
			}


			//
			//now do the post hmoebox action
			//
			JSONObject aDeneWordToUpdate;
			String pointerToDeneWord;
			Object valueToUpdate;
			for (Map.Entry<JSONObject, Integer> action : postHomeBoxActionEvaluationPositionActionIndex) {
				actionJSONObject = action.getKey();
				if(actionJSONObject.has(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE) && actionJSONObject.getString(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE).equals(TeleonomeConstants.SPERM_DENE_TYPE_UPDATE_DENEWORD_VALUE)){
					String updateListPointer = (String)DenomeUtils.getDeneWordAttributeByDeneWordNameFromDene( actionJSONObject, TeleonomeConstants.SPERM_ACTION_DENWORD_UPDATE_VALUE_LIST, TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);  
					FertilizationIdentity updateListIdentity = new FertilizationIdentity(updateListPointer);
					logger.debug("adding updateListPointer=" + updateListPointer);

					JSONObject updateListDene = FertilizationUtils.getDeneBySpermIdentity( completeSpermJSONObject,  updateListIdentity) ;
					JSONArray updatesJSONArray = updateListDene.getJSONArray("DeneWords");
					for(int i=0;i<updatesJSONArray.length();i++){
						aDeneWordToUpdate = updatesJSONArray.getJSONObject(i);
						pointerToDeneWord = aDeneWordToUpdate.getString(TeleonomeConstants.SPERM_ACTION_DENE_TARGET);
						valueToUpdate = aDeneWordToUpdate.get(TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);
						pulseJSONObject= DenomeUtils.updateDeneWordByIdentity(pulseJSONObject, pointerToDeneWord, valueToUpdate);

					}
				}
			}

			//
			// then do the mutations, which aremoved directly
			if(mutationsJSONArray!=null && mutationsJSONArray.length()>0) {
				JSONObject mutationJSONObject;
				for(int i=0;i<mutationsJSONArray.length();i++){
					mutationJSONObject = mutationsJSONArray.getJSONObject(i);
					if(!DenomeUtils.containsMutation(pulseJSONObject, mutationJSONObject.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE))) {
						DenomeUtils.addMutationToMutations(pulseJSONObject, mutationJSONObject);
					}
				}
			}


			String newTeleonomeInString = pulseJSONObject.toString(4);

			//
			// before validation write out the denome to file in case
			// there is a prblem
			new File(dataDirectory + "Teleonome.PreValidation.denome").delete();
			FileUtils.write(new File("Teleonome.PreValidation.denome"), newTeleonomeInString);
			//
			// now validate the new denome to see if there are errors
			//
			JSONArray validationErrors=null;
			try {
				validationErrors = DenomeValidator.validate(newTeleonomeInString);
				if(validationErrors.length()>0) {
					logger.warn("There were validation errors in the new denome.");
					for(int i=0;i<validationErrors.length();i++) {
						logger.warn(validationErrors.getJSONObject(i).toString(4));
					}
				}
			} catch (MissingDenomeException e) {
				// TODO Auto-generated catch block
				logger.warn(Utils.getStringException(e));
			}

			//
			// if there were no validation errors, then 
			// generate a report and 
			// render the new Telenome
			//
			if(validationErrors!=null && validationErrors.length()>0) {
				logger.warn(" ");
				logger.warn("The fertilization produced a malformed Denome");
				logger.warn("Reverting to previous state");
				logger.warn("The bad denome was stored in Teleonome.bad.denome");
				
				logger.warn(" ");
				//
				// save the bad teleonome.denome to examin it
				//
				new File(dataDirectory + "Teleonome.bad.denome").delete();
				FileUtils.write(new File("Teleonome.bad.denome"), newTeleonomeInString);

				undoFertilization();
			}else {
				//
				// the Teleonome.denome file
				//
				new File(dataDirectory + "Teleonome.PreValidation.denome").delete();
				new File(dataDirectory + "Teleonome.denome").delete();
				FileUtils.write(new File("Teleonome.denome"), newTeleonomeInString);
				
				//
				// now write the phisiology reprt
				//
				ArrayList<String> htmlArrayList;
				try {
					htmlArrayList = DenomeUtils.generateDenomePhysiologyReportHTMLTable(pulseJSONObject);
					String htmlText = String.join( System.getProperty("line.separator"), htmlArrayList);
					new File(dataDirectory + "TeleonomeAnalisis.html").delete();
					FileUtils.write(new File(dataDirectory +"TeleonomeAnalisis.html"), htmlText);
					
				} catch (MissingDenomeException | TeleonomeValidationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				logger.warn("Fertilization Process Complete.");
				logger.warn(" ");
				logger.warn("look for:");
				logger.warn("/home/pi/Teleonome.denome");
				logger.warn("/home/pi/avocado/TeleonomeAnalisis.html");
				
				
				logger.warn("Fertlization Completed");

			}


		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.debug(Utils.getStringException(e));
			System.out.println(Utils.getStringException(e));

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			logger.debug(Utils.getStringException(e));
		} catch (InvalidDenomeException e) {
			// TODO Auto-generated catch block
			logger.debug(Utils.getStringException(e));
		}
	}

	private static String getLastFertilizationDate() {
		File srcFolder= new File(dataDirectory+"Sperm_Fert");
		File[] files = srcFolder.listFiles();
		Arrays.sort(files, new Comparator<File>(){
			public int compare(File f1, File f2)
			{
				return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
			} });

		// take the first element of the array
		SimpleDateFormat dateFormat = new SimpleDateFormat(TeleonomeConstants.SPERM_DATE_FORMAT);
		return files[0].getName();

	}


	private static void undoFertilization() {
		SimpleDateFormat dateFormat = new SimpleDateFormat(TeleonomeConstants.SPERM_DATE_FORMAT);
		String destFolderName=dataDirectory;//"/home/pi/Teleonome/" ;

		//
		// first identify the folrders 
		File srcFolder= new File(dataDirectory + "Sperm_Fert"); //"/home/pi/Teleonome/Sperm_Fert");

		File[] files = srcFolder.listFiles();

		Arrays.sort(files, new Comparator<File>(){
			public int compare(File f1, File f2)
			{
				return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
			} });

		// take the first element of the array
		File selectedSourceFolder = files[0];
		String srcFolderName=selectedSourceFolder.getAbsolutePath();

		logger.debug("The last fertilization is " + selectedSourceFolder.getAbsolutePath());
		File destFolder = new File(destFolderName);
		//
		// copy the Teleonome.denome from fertilizatin back to main Teleonome
		//
		File srcFile =  new File(srcFolderName + "/TeleonomePreFertilization.denome");
		File destFile = new File(dataDirectory + "Teleonome.denome");
		//
		// First delete the file
		if(destFile.isFile()) {
			logger.debug("Erasing existing Teleonome.denome");
			destFile.delete();
		}

		try {
			FileUtils.copyFile(srcFile, destFile);
			logger.debug("copying prefertilizartion to Teleonome.denome");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//
		// now copy the sperm file back up
		//
		FileFilter fileFilter = new WildcardFileFilter("*.sperm");
		File[] spermFiles = selectedSourceFolder.listFiles(fileFilter);
		logger.debug("Number of Sperm files: " + spermFiles.length);
		File spermFile;
		for (int i = 0; i < spermFiles.length; i++) {
			spermFile = spermFiles[i];

			//
			//  copy the file
			//
			try {
				destFile = new File(dataDirectory  + spermFile.getName());
				logger.debug("about to copy sperm file existing "+ spermFile.getAbsolutePath() + "   " + spermFile.isFile() +  " to " + destFile.getAbsolutePath());
				FileUtils.copyFile(spermFile, destFile);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				logger.warn(Utils.getStringException(e));
			}

			//		   //
			//		   // check if the sperm file exists in 
			//		   destFile = new File("/home/pi/Teleonome/" + spermFile.getName());
			//		   if(destFile.isFile()) {
			//			   logger.debug("Erasing existing " + destFile);
			//				destFile.delete();
			//		   }


		}
		//
		// finally remove the directory
		logger.debug("Erasing fertilization directory " + selectedSourceFolder.getAbsolutePath());
		try {
			FileUtils.deleteDirectory(selectedSourceFolder);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.warn(Utils.getStringException(e));
		}

	}

	private void moveFiles() {
		SimpleDateFormat dateFormat = new SimpleDateFormat(TeleonomeConstants.SPERM_DATE_FORMAT);
		String srcFolderName=dataDirectory;//"/home/pi/Teleonome/" ;
		String destFolderName=dataDirectory + "Sperm_Fert/" + dateFormat.format(new Timestamp(System.currentTimeMillis())) + "/";
		File destFolder = new File(destFolderName);
		destFolder.mkdirs();
		File srcFile = new File(srcFolderName + "Teleonome.denome");
		File destFile =  new File(destFolderName + "TeleonomePreFertilization.denome");
		try {
			FileUtils.copyFile(srcFile, destFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		srcFile = new File(srcFolderName + spermFileName);
		destFile =  new File(destFolderName + spermFileName);
		try {
			FileUtils.copyFile(srcFile, destFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	class IntegerCompare implements Comparator<Map.Entry<?, Integer>>{
		public int compare(Map.Entry<?, Integer> o1, Map.Entry<?, Integer> o2) {
			return o1.getValue().compareTo(o2.getValue());
		}
	}

	public static void main(String[] args) {

		if(args.length!=1){
			System.out.println("Usage: fertilizer SpermFileName ");
			System.out.println(" ");
			System.out.println("Note: Sperm file should be inside of avocado folder");

			System.exit(-1);
		}

		String fileName =  Utils.getLocalDirectory() + "lib/Log4J.properties";
		PropertyConfigurator.configure(fileName);
		logger = Logger.getLogger(com.teleonome.fertilizer.Fertilizer.class);

		if(args[0].equals("-u")) {
			String previousStateDate = getLastFertilizationDate();
			Scanner scanner = new Scanner(System.in);
			System.out.println("Are you sure you want to revert to previous state  " + previousStateDate + " ? (Y/n)");
			String command = scanner.nextLine();
			String line;

			if(command.equals("Y")) {
				logger.warn("Reverting to previous state");
				undoFertilization();
			}else {
				System.out.println("Goodbye");
				System.exit(0);
			}
			scanner.close();
		}else {
			spermFileName=args[0];
			//eggTeleonomeLocation = args[1];

			//		spermFileName="/Users/arifainchtein/Data/Teleonome/Ra/AddGeneratorStatus.sperm";
			eggTeleonomeLocation= dataDirectory + "Teleonome.denome";
			File f = new File(dataDirectory + spermFileName);
			if(!f.isFile()){
				System.out.println("Sperm file is invalid: " + spermFileName);
				System.exit(-1);
			}
			f = new File(eggTeleonomeLocation);
			if(!f.isFile()){
				System.out.println("eggTeleonomeLocation file is invalid: " + eggTeleonomeLocation);
				System.exit(-1);
			}
			new Fertilizer();
		}

	}

}
