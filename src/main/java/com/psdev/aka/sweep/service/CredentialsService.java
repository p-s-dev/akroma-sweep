package com.psdev.aka.sweep.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.WalletUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static javax.management.timer.Timer.ONE_SECOND;

@Service
@Slf4j
public class CredentialsService {

    @Value("${keys.file.path}")
    String keysDirectory;

    @Value("#{'${keys.passwords}'.split(',')}")
    List<String> passwords;

    private ConcurrentHashMap<String, Credentials> credentialsMap = new ConcurrentHashMap();

    @Scheduled(fixedDelay = ONE_SECOND*10, initialDelay=ONE_SECOND*5)
    public void checkNewFilesInLocalKeystore() {

        getKeystoreFiles().stream().forEach((String keystoreFilePath) -> {
            if (!credentialsMap.containsKey(keystoreFilePath)) {
                Credentials credentials = findValidPassword(keystoreFilePath);
                if (credentials != null) {
                    credentialsMap.put(keystoreFilePath, credentials);
                    log.info("keystore added to credentials monitoring list: " + keystoreFilePath);
                } else {
                    log.error("unable to add keystore, no valid password: " + keystoreFilePath);
                }
            }
        });

        // for all known keyfilepaths, if filesystem directory does not contain keyfilepath, remove
        credentialsMap.keySet().stream()
                .filter(keystoreFilePath -> !getKeystoreFiles().contains(keystoreFilePath))
                .forEach(c -> {
            credentialsMap.remove(c);
            log.info("keystore removed from credentials monitoring list: " + c);
        });


    }

    private Credentials findValidPassword(String keystoreFile) {
        Credentials c;
        for (String password: passwords) {
            try {
                c = WalletUtils.loadCredentials(password, keystoreFile);
                return c;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (CipherException e) {
                if (e.getMessage().equals("Invalid password provided")) {
                    log.debug("Wrong password, trying another");
                } else {
                    e.printStackTrace();
                }
            }
        }
        return null;

    }

    private List<String> getKeystoreFiles() {
        File folder = new File(keysDirectory);
        File[] files = folder.listFiles();
        if (files != null) {
            return Arrays.asList(files)
                    .stream().filter(f -> f.getName().startsWith("UTC--"))
                    .map(File::getAbsolutePath).collect(Collectors.toList());
        } else {
            log.error("No keystore files.");
            return Collections.EMPTY_LIST;
        }
    }

    public Collection<Credentials> getCredentials() {
        return credentialsMap.values();
    }

    public Collection<String> getCredentialAddresses() {

        return new ArrayList(credentialsMap.values().stream()
                .map(Credentials::getAddress)
                .map(Keys::toChecksumAddress)
                .collect(Collectors.toList()));
    }

    public boolean containsAddress(String address) {
        return getCredentialAddresses().contains(address);
    }


}
