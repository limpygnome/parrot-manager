package com.limpygnome.parrot.config;

import com.limpygnome.parrot.library.crypto.CryptoParamsFactory;
import com.limpygnome.parrot.library.db.DatabaseReaderWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to inject instances of POJOs, which can be used as singletons, as beans, from the parrot library.
 */
@Configuration
public class LibraryBeansConfig
{

    @Bean
    public DatabaseReaderWriter databaseReaderWriter()
    {
        return new DatabaseReaderWriter();
    }

    @Bean
    public CryptoParamsFactory cryptoParamsFactory()
    {
        return new CryptoParamsFactory();
    }

}