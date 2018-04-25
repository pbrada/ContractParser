package cz.zcu.kiv.contractparser.comparator;

import cz.zcu.kiv.contractparser.ContractExtractorApi;
import cz.zcu.kiv.contractparser.utils.ResourceHandler;
import cz.zcu.kiv.contractparser.utils.IOServices;
import cz.zcu.kiv.contractparser.model.JavaFile;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.List;

/**
 * Provides means to compare two folders containing Java files. It compares individual files and determines what changes
 * there were made on API and contracts.
 *
 * @author Vaclav Mares
 */
public class JavaFolderComparator {

    /** Log4j logger for this class */
    private final static Logger logger = Logger.getLogger(String.valueOf(JavaFolderComparator.class));

    /**
     * This method compares two folders containing Java files. It compares individual files and determines what changes
     * there were made on API and contracts (based on input parameters). Output is a report object which contains
     * information such as what files were added/removed, individual file compare reports and summary statistics.
     * Compared data can be exported to JSON right away to avoid memory usage spikes.
     *
     * @param thisFolder                File - first folder with Java files
     * @param otherFolder               File - second folder with Java files
     * @param reportEqual               boolean - whether should equal files be reported or not
     * @param reportNonContractChanges  boolean - whether should be reported changes that doesn't affect contracts
     * @param exportToJson              boolean - whether should be data exported to JSON right away
     * @param jsonOutputFolder          File - output folder for JSON i case of enabled JSON export
     * @param prettyPrint               boolean - if JSON should be in human readable form (otherwise JSON min)
     * @return                          JavaFolderCompareReport - containing all relevant compare information
     */
    public JavaFolderCompareReport compareJavaFolders(File thisFolder, File otherFolder, boolean reportEqual,
                                      boolean reportNonContractChanges, boolean exportToJson, File jsonOutputFolder,
                                      boolean prettyPrint) {

        JavaFolderCompareReport javaFolderCompareReport = new JavaFolderCompareReport(thisFolder.getAbsolutePath(),
                otherFolder.getAbsolutePath());

        int contractsAdded = 0;
        int contractsRemoved = 0;

        // check if folders exist and that they are folders
        if(!IOServices.checkFolder(thisFolder) || !IOServices.checkFolder(otherFolder)){
            return null;
        }

        // if export to JSON is desired but the output folder is not in order - return null
        if(exportToJson && !IOServices.checkFolder(jsonOutputFolder)) {
            return null;
        }

        String thisFolderPath = thisFolder.getAbsolutePath();
        String otherFolderPath = otherFolder.getAbsolutePath();

        // retrieve contracts from both folders
        List<JavaFile> thisJavaFiles = ContractExtractorApi.retrieveContractsFromFolder(thisFolder, false);
        List<JavaFile> otherJavaFiles = ContractExtractorApi.retrieveContractsFromFolder(otherFolder, false);

        for(JavaFile thisJavaFile : thisJavaFiles) {

            // if non contract changes should not be reported and if there are not any contracts in file - skip it
            if(!reportNonContractChanges && thisJavaFile.getJavaFileStatistics().getTotalNumberOfContracts() == 0){
                continue;
            }

            // create path difference to find files in the other folder
            String thisJavaFilePath;
            if(thisFolderPath.length() + 1 < thisJavaFile.getFullPath().length()){
                thisJavaFilePath = thisJavaFile.getFullPath().substring(thisFolderPath.length() + 1);
            }
            else{
                thisJavaFilePath = thisJavaFile.getFullPath();
            }

            // try to find pair file int the other folder by comparing relative paths
            int otherJavaFileId = 0;
            boolean fileFound = false;
            while(otherJavaFileId < otherJavaFiles.size()){

                String otherJavaFilePath;
                if(otherFolderPath.length() + 1 < otherJavaFiles.get(otherJavaFileId).getFullPath().length()) {
                    otherJavaFilePath = otherJavaFiles.get(otherJavaFileId).getFullPath()
                            .substring(otherFolderPath.length() + 1);
                }
                else{
                    otherJavaFilePath = otherJavaFiles.get(otherJavaFileId).getFullPath();
                }

                if(thisJavaFilePath.compareTo(otherJavaFilePath) == 0){
                    fileFound = true;
                    break;
                }

                otherJavaFileId++;
            }

            // if pair file was found compare those files and add java file report to the folder report
            if(fileFound){

                JavaFileCompareReport javaFileCompareReport = thisJavaFile.compareJavaFileTo(
                        otherJavaFiles.get(otherJavaFileId), reportEqual, reportNonContractChanges);

                if(javaFileCompareReport != null && 
                        (reportNonContractChanges || !javaFileCompareReport.isContractEqual())) {

                    // if reports should be exported to JSON right away - do so - otherwise add it to the report list
                    if(exportToJson){
                        String filename = IOServices.escapeFilePath(thisJavaFilePath);
                        IOServices.saveObjectAsJson(javaFileCompareReport, filename, jsonOutputFolder, prettyPrint);
                    }
                    else{
                        javaFolderCompareReport.addJavaFileCompareReport(javaFileCompareReport);
                    }
                }

                logger.info(ResourceHandler.getMessage("infoCompareFound", thisJavaFilePath));

                otherJavaFiles.remove(otherJavaFileId);
            }
            else{
                // if pair file was not found - add it to list of removed files
                javaFolderCompareReport.addFileRemoved(thisJavaFilePath);
                contractsRemoved += thisJavaFile.getJavaFileStatistics().getTotalNumberOfContracts();
                logger.info(ResourceHandler.getMessage("infoCompareRemoved", thisJavaFilePath));
            }
        }

        // go through all remaining files in the other folder
        for(JavaFile otherJavaFile : otherJavaFiles) {

            // if non contract changes should not be reported and if there are not any contracts in file - skip it
            if(!reportNonContractChanges && otherJavaFile.getJavaFileStatistics().getTotalNumberOfContracts() == 0){
                continue;
            }

            // add it to the list with added files
            String otherJavaFilePath;
            if(otherFolderPath.length() + 1 < otherJavaFile.getFullPath().length()) {
                otherJavaFilePath = otherJavaFile.getFullPath().substring(otherFolderPath.length() + 1);
            }
            else{
                otherJavaFilePath = otherJavaFile.getFullPath();
            }

            javaFolderCompareReport.addFileAdded(otherJavaFilePath);
            contractsAdded += otherJavaFile.getJavaFileStatistics().getTotalNumberOfContracts();
            logger.info(ResourceHandler.getMessage("infoCompareAdd", otherJavaFilePath));
        }

        // create folder statistics
        int filesAdded = javaFolderCompareReport.getFilesAdded().size();
        int filesRemoved = javaFolderCompareReport.getFilesRemoved().size();
        javaFolderCompareReport.setJavaFolderCompareStatistics(new JavaFolderCompareStatistics(filesAdded, filesRemoved,
                contractsAdded, contractsRemoved));


        if(exportToJson){
            IOServices.saveObjectAsJson(javaFolderCompareReport, ResourceHandler.getProperties().getString(
                    "globalStatisticsFile"), jsonOutputFolder, prettyPrint);
        }
        
        return javaFolderCompareReport;
    }


    /**
     * Exports given JavaCompareFolder to JSON. Each JavaFileCompareReport is saved in separated file and there is
     * also crete one file for global information.
     *
     * @param javaFolderCompareReport  JavaFolderCompareReport to be exported
     * @param outputFolder             File representing folder where should be export saved
     * @param prettyPrint              Whether JSON should be in human readable form or not
     * @return                         boolean - true if at least one file was exported
     */
    public boolean exportJavaCompareFolderToJson(JavaFolderCompareReport javaFolderCompareReport,
                                                        File outputFolder, boolean prettyPrint) {
        
        boolean success = false;

        for(JavaFileCompareReport javaFileCompareReport : javaFolderCompareReport.getJavaFileCompareReports()){

            String filename = IOServices.escapeFilePath(javaFileCompareReport.getThisFilePath());
            if(IOServices.saveObjectAsJson(javaFileCompareReport, filename, outputFolder, prettyPrint)){
                success = true;
            }
        }

        if(success){
            IOServices.saveObjectAsJson(javaFolderCompareReport, ResourceHandler.getProperties().getString(
                    "globalStatisticsFile"), outputFolder, prettyPrint);
        }

        return success;
    }
}
