package com.advika.energysource.oraclefusionjournalsimport.beans;

import com.oracle.xmlns.adf.svc.errors.ServiceException;
import org.advika.erpIntegrationService.DocumentDetails;
import org.advika.erpIntegrationService.ErpIntegrationService;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Component
public class WebServiceClient {

    @Value("${fusion.wsdlUrl}")
    private String wsdlUrlString;

    @Value("${fusion.user}")
    private String user;

    @Value("${fusion.encodedpwd}")
    private String encodedPwd;

    @Value("${fusion.importmainttables.jobpackagename}")
    private String mainJobPackageName;

    @Value("${fusion.importmainttables.jobdefinitionname}")
    private String mainJobDefinitionName;

    @Value("${fusion.importmaintables.params}")
    private String importToMainTablesParams;

    @Value("${fusion.uploadtoucm.contenttype}")
    private String contentType;

    @Value("${fusion.uplaodtoucm.securitygroup}")
    private String securityGroup;

    @Value("${fusion.uploadtoucm.documentaccount}")
    private String documentAccount;

    @Value("${fusion.importinterfacetable.jobpackagename}")
    private String interfaceJobPackageName;

    @Value("${fusion.importinterfacetable.jobdefinitionname}")
    private String interfaceJobDefinitionName;

    @Value("${fusion.importinterfacetable.importprocess}")
    private String importProcess;

    private static final Logger Log = LoggerFactory.getLogger(WebServiceClient.class);

    private static final QName SERVICE_NAME =
            new QName("http://xmlns.oracle.com/apps/financials/commonModules/shared/model/erpIntegrationService/", "ErpIntegrationService");

    // Singleton CXF proxy — created once at startup
    private ErpIntegrationService port;

    @PostConstruct
    public void init() {
        String password = new String(Base64.getDecoder().decode(encodedPwd));

        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(ErpIntegrationService.class);
        factory.setAddress(wsdlUrlString.replace("?WSDL", ""));
        port = (ErpIntegrationService) factory.create();

        Client client = ClientProxy.getClient(port);
        HTTPConduit http = (HTTPConduit) client.getConduit();
        http.getAuthorization().setUserName(user);
        http.getAuthorization().setPassword(password);

        Log.info("Oracle Fusion ERP Integration SOAP proxy initialized");
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Retries {@code operation} up to {@code maxAttempts} times with linear backoff (2s, 4s, …).
     * Throws the last exception if all attempts are exhausted.
     */
    private <T> T withRetry(int maxAttempts, String opName, ThrowingSupplier<T> operation) throws Exception {
        int attempt = 0;
        while (true) {
            try {
                return operation.get();
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    Log.error("{} failed after {} attempt(s): {}", opName, maxAttempts, e.getMessage());
                    throw e;
                }
                long delayMs = 2000L * attempt;
                Log.warn("{} failed (attempt {}/{}), retrying in {}ms: {}", opName, attempt, maxAttempts, delayMs, e.getMessage());
                Thread.sleep(delayMs);
            }
        }
    }

    public EssJobStatus checkEssJobStatus(Long requestId, String jobName) throws InterruptedException, ServiceException {
        if (requestId == -1) {
            return EssJobStatus.ERROR;
        }

        EssJobStatus status;
        do {
            Thread.sleep(10000);
            status = EssJobStatus.from(port.getESSJobStatus(requestId));
            Log.info("{} status {}", jobName, status);
        } while (status.isInProgress());

        if (status.isSuccess()) {
            Log.info("{} completed successfully", jobName);
        } else if (status.isError()) {
            Log.info("{} completed in error. Status {}", jobName, status);
        } else {
            Log.info("{} finished with status {}", jobName, status);
        }

        return status;
    }

    private List<String> getEssJobsParams(String paramsString) {
        List<String> paramsList = new ArrayList<>();
        Collections.addAll(paramsList, paramsString.split(","));
        return paramsList;
    }

    public boolean client(byte[] data, String fileName) throws Exception {

        DocumentDetails doc = new DocumentDetails();
        doc.setContent(data);
        doc.setFileName(fileName);
        doc.setContentType(contentType);
        doc.setDocumentSecurityGroup(securityGroup);
        doc.setDocumentAccount(documentAccount);

        Log.info("Invoking upload to UCM process");

        String uploadToUcmId = withRetry(3, "uploadFileToUcm", () -> port.uploadFileToUcm(doc));

        List<String> submitEssJobParam = new ArrayList<>();
        submitEssJobParam.add(importProcess);
        submitEssJobParam.add(uploadToUcmId);
        submitEssJobParam.add("N");
        submitEssJobParam.add("N");
        submitEssJobParam.add("");

        long requestId = withRetry(3, "submitESSJobRequest (InterfaceLoader)",
                () -> port.submitESSJobRequest(interfaceJobPackageName, interfaceJobDefinitionName, submitEssJobParam));

        Log.info("Load Interface File for Import process request Id " + requestId);

        EssJobStatus processStatus = checkEssJobStatus(requestId, "Load Interface File for Import");

        if (!processStatus.isSuccess()) {
            return false;
        }

        Log.info("Submitting the Import Journals process");

        long importJournalsRequestId = withRetry(3, "submitESSJobRequest (JournalImport)",
                () -> port.submitESSJobRequest(mainJobPackageName, mainJobDefinitionName, getEssJobsParams(importToMainTablesParams)));

        Log.info("Import Journals process request Id " + importJournalsRequestId);

        processStatus = checkEssJobStatus(importJournalsRequestId, "Import Journals");

        if (processStatus.isSuccess()) {
            Log.info("Import journals completed successfully. Please validate the same in Oracle Fusion.");
        } else {
            Log.info("Import journals completed in error. Please check in Oracle Fusion.");
            return false;
        }

        return true;
    }
}
