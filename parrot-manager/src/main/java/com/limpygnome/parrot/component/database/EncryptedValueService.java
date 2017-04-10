package com.limpygnome.parrot.component.database;

import com.google.gson.JsonObject;
import com.limpygnome.parrot.library.crypto.EncryptedValue;
import com.limpygnome.parrot.library.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * A bridge between the presentation and persistence layer of encrypted data.
 */
@Service
public class EncryptedValueService
{
    @Autowired
    private DatabaseService databaseService;

    /**
     * Creates an encrypted value from a string.
     *
     * @param text value to be encrypted; can be null
     * @return an instance, or null if the provided value is null
     * @throws Exception
     */
    public EncryptedValue fromString(String text) throws Exception
    {
        EncryptedValue result = null;

        if (text != null)
        {
            Database database = getDatabase();
            byte[] data = text.getBytes("UTF-8");
            result = database.encrypt(data);
        }

        return result;
    }

    public String asString(EncryptedValue encryptedValue) throws Exception
    {
        String result = null;

        if (encryptedValue != null)
        {
            Database database = getDatabase();
            byte[] decrypted = database.decrypt(encryptedValue);

            if (decrypted != null)
            {
                result = new String(decrypted, "UTF-8");
            }
        }

        return result;
    }

    public EncryptedValue fromJson(JsonObject json) throws Exception
    {
        EncryptedValue result = null;

        if (json != null)
        {
            String text = json.toString();
            result = fromString(text);
        }

        return result;
    }

    private Database getDatabase()
    {
        return databaseService.getDatabase();
    }

}
