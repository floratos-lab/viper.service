package org.geworkbench.service.viper.service;

import javax.activation.DataHandler;

import org.geworkbench.service.viper.schema.ViperInput;

import java.io.IOException;

public interface ViperInputRepository {

    String storeViperInput(ViperInput input) throws IOException;

    DataHandler execute(ViperInput input, String dataDir, StringBuilder log) throws IOException;
}
