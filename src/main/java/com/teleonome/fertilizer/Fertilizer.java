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
import com.teleonome.framework.mnemosyne.MnemosyneManager;
import com.teleonome.framework.persistence.PostgresqlPersistenceManager;
import com.teleonome.framework.utils.Utils;



public class Fertilizer {
	static Logger logger;
	private PostgresqlPersistenceManager aDBManager=null;
	String denomeFileInString;
	private JSONObject pulseJSONObject;
	private String buildNumber="27/04/2018 15:47";
	private static String spermFileName;
	private static String eggTeleonomeLocation;

	public Fertilizer(){


		String fileName =  Utils.getLocalDirectory() + "lib/Log4J.properties";
		PropertyConfigurator.configure(fileName);
		logger = Logger.getLogger(getClass());
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
		try {
			int hypothalamusPid = Integer.parseInt(FileUtils.readFileToString(new File("PaceMakerProcess.info")).split("@")[0]);

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



		if(!continueProcess) {
			logger.info("There was a problem stopping the Hypothalamus, stopping the fertilization");
			System.exit(-1);
		}
		//
		// if we are here, then the hypothalamus has stopped
		// so  move the existing Teleonome to a new directory

		moveFiles();

		File selectedFile = new File(eggTeleonomeLocation);
		logger.debug("reading egg from " +eggTeleonomeLocation);

		try {
			denomeFileInString = FileUtils.readFileToString(selectedFile);
			pulseJSONObject = new JSONObject(denomeFileInString);


			//
			// now read the sperm
			String spermFileInString = FileUtils.readFileToString(new File(spermFileName));
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
				String teleonomeUpdatedReferences = teleonomeBackInString.replace("@Egg:", "@" + newTeleonomeName + ":");
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
			String newDeneChainName;
			Identity actionTargetIdentity, newDeneChainIdentity;

			for (Map.Entry<JSONObject, Integer> action : preHomeBoxActionEvaluationPositionActionIndex) {
				actionJSONObject = action.getKey();
				actionTargetPointer = actionJSONObject.getString(TeleonomeConstants.SPERM_ACTION_DENE_TARGET);
				if(actionJSONObject.has(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE) && actionJSONObject.getString(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE).equals(TeleonomeConstants.SPERM_DENE_TYPE_CREATE_DENE_CHAIN)){
					newDeneChainName = (String) DenomeUtils.getDeneWordAttributeByDeneWordNameFromDene(actionJSONObject, TeleonomeConstants.SPERM_ACTION_DENEWORD_DENECHAIN_NAME, TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);
					actionTargetIdentity = new Identity(actionTargetPointer);
					JSONObject newDeneChain = new JSONObject();
					newDeneChain.put(TeleonomeConstants.DENE_DENE_NAMEE_ATTRIBUTE, newDeneChainName);
					newDeneChain.put("Denes", new JSONArray());
					newDeneChainIdentity = new Identity (actionTargetPointer  + ":" + newDeneChainName);
					if(!DenomeUtils.containsDenomicElementByIdentity( pulseJSONObject, newDeneChainIdentity)) {
						DenomeUtils.addDeneChainToNucleusByIdentity( pulseJSONObject, newDeneChain,  actionTargetIdentity);
					}
				}
			}
			//
			// now do the homeboxes
			//
			Identity newDeneIdentity;
			for(int i=0;i<homeboxesJSONArray.length();i++){
				homeoboxJSONObject = homeboxesJSONArray.getJSONObject(i);
				allDenePointersForHomebox = new ArrayList();
				homeoboxDenes = homeoboxJSONObject.getJSONArray(TeleonomeConstants.SPERM_HOMEOBOX_DENES);
				for(int j=0;j<homeoboxDenes.length();j++){
					homeoboxDene = homeoboxDenes.getJSONObject(j);
					if(homeoboxDene.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE).equals(TeleonomeConstants.SPERM_HOMEOBOX_INDEX)){
						//
						// we are in the correct dene to get the index of all the other denes that need to be acted upon in this homebox
						// get the deneword of type Hox Dene Pointer
						homeoboxIndexDeneWords = homeoboxDene.getJSONArray("DeneWords");
						for(int k=0;k<homeoboxIndexDeneWords.length();k++){
							homeoboxIndexDeneWord = homeoboxIndexDeneWords.getJSONObject(k);
							if(homeoboxIndexDeneWord.getString(TeleonomeConstants.DENEWORD_DENEWORD_TYPE_ATTRIBUTE).equals(TeleonomeConstants.SPERM_HOX_DENE_POINTER)){
								denePointer =  homeoboxIndexDeneWord.getString(TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE);
								fertilizationIdentity = new FertilizationIdentity(denePointer);
								logger.debug("adding denePointer=" + denePointer);

								hoxDene = FertilizationUtils.getDeneBySpermIdentity( completeSpermJSONObject,  fertilizationIdentity) ;
								hoxDeneTargetPointer = hoxDene.getString(TeleonomeConstants.SPERM_HOX_DENE_TARGET);
								hoxDeneTargetIdentity = new Identity(hoxDeneTargetPointer);
								//
								// the hoxDeneTargetPointer contains a pointer to where this dene needs to be moved, first removed the target from the dene
								hoxDene.remove(TeleonomeConstants.SPERM_HOX_DENE_TARGET);
								//
								// now insert the dene into the teleonome
								newDeneIdentity = new Identity (hoxDeneTargetPointer  + ":" + hoxDene.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE));
								logger.debug("adding1 hoxDeneTargetIdentity=" + hoxDeneTargetIdentity + " HoxDene Name=" + hoxDene.getString("Name")  + " newDeneIdentity=" + newDeneIdentity.toString());
								
								if(!DenomeUtils.containsDenomicElementByIdentity( pulseJSONObject, newDeneIdentity)) {
									DenomeUtils.addDeneToDeneChainByIdentity( pulseJSONObject, hoxDene,  hoxDeneTargetIdentity);
								}else {
									logger.debug("Did not add " + hoxDeneTargetIdentity +":"+ hoxDene.getString("Name") + " because it already existed" );
								}
							}
						}
					}
					//
					// the code above will move entire denes.  now do it again but look for the dene type "DeneWOrd Carrier"
					// which is used to insert denewords into existing denes.  there could be many of this because every dene will
					// have a target which represents the dene where the denewords will be inserted.
					// The loop is run again, to make sure that all the denes are inserted first, then the denewords
					JSONObject homeoboxCarrierDeneWord;
					Identity newDeneWordIdentity;
					if(homeoboxDene.has(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE) &&
						homeoboxDene.get(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE).equals(TeleonomeConstants.SPERM_DENE_TYPE_DENEWORD_CARRIER)){
						JSONArray carrierDeneWords = homeoboxDene.getJSONArray("DeneWords");
						String carrierDeneTargetPointer = homeoboxDene.getString(TeleonomeConstants.SPERM_HOX_DENE_TARGET);
						Identity carrierDeneTargetIdentity = new Identity(carrierDeneTargetPointer);

						for(int k=0;k<carrierDeneWords.length();k++){
							homeoboxCarrierDeneWord = carrierDeneWords.getJSONObject(k);
							
							
							newDeneWordIdentity = new Identity (carrierDeneTargetPointer  + ":" + homeoboxCarrierDeneWord.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE));
							logger.debug("adding1 hoxDeneTargetIdentity=" + carrierDeneTargetIdentity.toString() + " homeoboxCarrierDeneWord Name=" + homeoboxCarrierDeneWord.getString("Name")  + " newDeneWordIdentity=" + newDeneWordIdentity.toString());
							
							if(!DenomeUtils.containsDenomicElementByIdentity( pulseJSONObject, newDeneWordIdentity)) {
								DenomeUtils.addDeneWordToDeneByIdentity( pulseJSONObject, homeoboxCarrierDeneWord,  carrierDeneTargetIdentity);	
							}else {
								logger.debug("Did not add " + carrierDeneTargetIdentity +":"+ homeoboxCarrierDeneWord.getString("Name") + " because it already existed" );
							
							}
						}
					}
					
					//
					// now do it again but look for the dene type "DeneWOrd Remover"
					// which is used to remove denewords from existing denes.  
					JSONObject homeoboxRemoverDeneWord;
					Identity targetDeneWordIdentity;
					if(homeoboxDene.has(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE) &&
						homeoboxDene.get(TeleonomeConstants.DENE_DENE_TYPE_ATTRIBUTE).equals(TeleonomeConstants.SPERM_DENE_TYPE_DENEWORD_REMOVER)){
						JSONArray removerDeneWords = homeoboxDene.getJSONArray("DeneWords");
						String removerDeneTargetPointer = homeoboxDene.getString(TeleonomeConstants.SPERM_HOX_DENE_TARGET);
						Identity removerDeneTargetIdentity = new Identity(removerDeneTargetPointer);

						for(int k=0;k<removerDeneWords.length();k++){
							homeoboxRemoverDeneWord = removerDeneWords.getJSONObject(k);
							targetDeneWordIdentity = new Identity(homeoboxRemoverDeneWord.getString(TeleonomeConstants.DENEWORD_VALUE_ATTRIBUTE));	
							boolean removed = DenomeUtils.removeDeneWordFromDeneByIdentity( pulseJSONObject, targetDeneWordIdentity);
							
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
			JSONObject mutationJSONObject;
			for(int i=0;i<mutationsJSONArray.length();i++){
				mutationJSONObject = mutationsJSONArray.getJSONObject(i);
				if(!DenomeUtils.containsMutation(pulseJSONObject, mutationJSONObject.getString(TeleonomeConstants.DENEWORD_NAME_ATTRIBUTE))) {
					DenomeUtils.addMutationToMutations(pulseJSONObject, mutationJSONObject);
				}
			}
			//
			// the last step is to render the new Telenome
			//
			String newTeleonomeInString = pulseJSONObject.toString(4);
			new File("Teleonome.denome").delete();
			FileUtils.write(new File("Teleonome.denome"), newTeleonomeInString);

			
			//
			// now validate the new denome to see if there are errors
			//
			try {
				JSONArray validationErrors = DenomeValidator.validate(newTeleonomeInString);
				if(validationErrors.length()>0) {
					System.out.println("There were validation errors in the new denome.");
					for(int i=0;i<validationErrors.length();i++) {
						System.out.println(validationErrors.getJSONObject(i).toString(4));
					}
				}

				System.out.println("Fertlization Completed");

			} catch (MissingDenomeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
		File srcFolder= new File("/home/pi/Teleonome/Sperm_Fert");
		File[] files = srcFolder.listFiles();
		Arrays.sort(files, new Comparator<File>(){
		    public int compare(File f1, File f2)
		    {
		        return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
		    } });
		
		// take the first element of the array
		SimpleDateFormat dateFormat = new SimpleDateFormat(TeleonomeConstants.SPERM_DATE_FORMAT);
		return files[0].getName();
		
	}
	
	
	private static void undoFertilization() {
		SimpleDateFormat dateFormat = new SimpleDateFormat(TeleonomeConstants.SPERM_DATE_FORMAT);
		String destFolderName="/home/pi/Teleonome/" ;
		
		//
		// first identify the folrders 
		File srcFolder= new File("/home/pi/Teleonome/Sperm_Fert");
				
		File[] files = srcFolder.listFiles();

		Arrays.sort(files, new Comparator<File>(){
		    public int compare(File f1, File f2)
		    {
		        return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
		    } });
		
		// take the first element of the array
		File selectedSourceFolder = files[0];
		String srcFolderName=selectedSourceFolder.getAbsolutePath();
		
		logger.debug("The last fertilization is " + selectedSourceFolder.getAbsolutePath());
		File destFolder = new File(destFolderName);
		//
		// copy the Teleonome.denome from fertilizatin back to main Teleonome
		//
		File srcFile =  new File(srcFolderName + "TeleonomePreFertilization.denome");
		File destFile = new File("/home/pi/Teleonome/Teleonome.denome");
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
		File dir = new File(".");
		FileFilter fileFilter = new WildcardFileFilter("*.sperm");
		File[] spermFiles = dir.listFiles(fileFilter);
		File spermFile;
		for (int i = 0; i < files.length; i++) {
		   spermFile = spermFiles[i];
		   //
		   // check if the sperm file exists in 
		   destFile = new File("/home/pi/Teleonome/" + spermFile.getName());
		   if(destFile.isFile()) {
			   logger.debug("Erasing existing " + destFile);
				destFile.delete();
		   }
		   
		   //
		   // now copy the file
		  //
			try {
				FileUtils.copyFile(spermFile, destFile);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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
		String srcFolderName="/home/pi/Teleonome/" ;
		String destFolderName="/home/pi/Teleonome/Sperm_Fert/" + dateFormat.format(new Timestamp(System.currentTimeMillis())) + "/";
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
			System.out.println("Usage: fertilizer completePathSpermFileName ");
			System.exit(-1);
		}
		if(args[0].equals("-u")) {
			String previousStateDate = getLastFertilizationDate();
			Scanner scanner = new Scanner(System.in);
			System.out.println("Are you sure you want to revert to previous state  " + previousStateDate + " (Y/n)");
			String command = scanner.nextLine();
			String line;
			System.out.println("command is " + command);
			if(command.equals("Y")) {
				undoFertilization();
			}
			scanner.close();
		}else {
			spermFileName=args[0];
			//eggTeleonomeLocation = args[1];

			//		spermFileName="/Users/arifainchtein/Data/Teleonome/Ra/AddGeneratorStatus.sperm";
			eggTeleonomeLocation="Teleonome.denome";
			File f = new File(spermFileName);
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
