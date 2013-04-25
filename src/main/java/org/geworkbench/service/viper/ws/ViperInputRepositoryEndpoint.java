package org.geworkbench.service.viper.ws;

import java.io.IOException;

import javax.activation.DataHandler;
import javax.xml.bind.JAXBElement;

import org.springframework.util.Assert;
import org.geworkbench.service.viper.schema.ViperInput;
import org.geworkbench.service.viper.schema.ObjectFactory;
import org.geworkbench.service.viper.schema.ViperOutput;
import org.geworkbench.service.viper.service.ViperInputRepository;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class ViperInputRepositoryEndpoint {

    private ViperInputRepository viperInputRepository;

    private ObjectFactory objectFactory;

    public ViperInputRepositoryEndpoint(ViperInputRepository viperInputRepository) {
        Assert.notNull(viperInputRepository, "'viperInputRepository' must not be null");
        this.viperInputRepository = viperInputRepository;
        this.objectFactory = new ObjectFactory();
    }

    @PayloadRoot(localPart = "ExecuteViperRequest", namespace = "http://www.geworkbench.org/service/viper")
    @ResponsePayload
    public JAXBElement<ViperOutput> executeViper(@RequestPayload JAXBElement<ViperInput> requestElement) throws IOException {
    	ViperInput request = requestElement.getValue();

        String dataDir = viperInputRepository.storeViperInput(request);
        
        DataHandler handler = null;
        StringBuilder log = new StringBuilder();
        if (dataDir == null)
        	log.append("Cannot find data dir to store viper input");
        else
        	handler = viperInputRepository.execute(request, dataDir, log);

        ViperOutput response = new ViperOutput();
        response.setLog(log.toString());
        response.setOutfile(handler);
        return objectFactory.createExecuteViperResponse(response);
    }
}
