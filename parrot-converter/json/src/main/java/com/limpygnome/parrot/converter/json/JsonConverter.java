package com.limpygnome.parrot.converter.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limpygnome.parrot.converter.api.Converter;
import com.limpygnome.parrot.converter.api.MalformedInputException;
import com.limpygnome.parrot.converter.api.ConversionException;
import com.limpygnome.parrot.converter.api.Options;
import com.limpygnome.parrot.lib.database.EncryptedValueService;
import com.limpygnome.parrot.lib.io.StringStreamOperations;
import com.limpygnome.parrot.library.crypto.EncryptedValue;
import com.limpygnome.parrot.library.db.Database;
import com.limpygnome.parrot.library.db.DatabaseMerger;
import com.limpygnome.parrot.library.db.DatabaseNode;
import com.limpygnome.parrot.library.db.MergeLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

@Component("json")
public class JsonConverter implements Converter
{
    @Autowired
    private StringStreamOperations stringStreamOperations;
    @Autowired
    private EncryptedValueService encryptedValueService;

    @Override
    public String[] databaseImport(Database database, Options options, InputStream inputStream) throws ConversionException, MalformedInputException, IOException
    {
        String text = stringStreamOperations.readString(inputStream);
        return databaseImportText(database, options, text);
    }

    @Override
    public void databaseExport(Database database, Options options, OutputStream outputStream) throws ConversionException, IOException
    {
        String text = databaseExportText(database, options);
        stringStreamOperations.writeString(outputStream, text);
    }

    @Override
    public String[] databaseImportText(Database database, Options options, String text) throws ConversionException, MalformedInputException
    {
        // parse to json
        JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(text);

        if (!element.isJsonObject())
        {
            throw new ConversionException("Malformed database - expected first element to be a json object", null);
        }

        JsonObject jsonRoot = element.getAsJsonObject();

        // convert to database
        Database databaseParsed = new Database(database.getMemoryCryptoParams(), database.getFileCryptoParams());
        DatabaseNode root = databaseParsed.getRoot();

        importAddChildren(databaseParsed, root, jsonRoot);

        // merge with current database
        try
        {
            DatabaseMerger merger = new DatabaseMerger();
            MergeLog mergeLog = merger.merge(databaseParsed, database, null);
            List<String> listMessages = mergeLog.getMessages();
            String[] messages = listMessages.toArray(new String[listMessages.size()]);
            return messages;
        }
        catch (Exception e)
        {
            throw new ConversionException("Failed to merge database - " + e.getMessage(), e);
        }
    }

    @Override
    public String databaseExportText(Database database, Options options) throws ConversionException
    {
        DatabaseNode root = database.getRoot();
        JsonObject jsonRoot = new JsonObject();

        // iterate and add all DB children
        exportAddChildren(database, root, jsonRoot);

        // convert to pretty string
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String text = gson.toJson(jsonRoot);

        return text;
    }

    private void importAddChildren(Database database, DatabaseNode root, JsonObject jsonRoot) throws ConversionException
    {
        // parse properties
        if (!root.isRoot())
        {
            // -- id
            if (jsonRoot.has("id"))
            {
                String id = jsonRoot.get("id").getAsString();
                root.setId(UUID.fromString(id));
            }

            // -- name
            String name;
            if (jsonRoot.has("name"))
            {
                name = jsonRoot.get("name").getAsString();
                root.setName(name);
            }
            else
            {
                name = "";
            }

            // -- value
            if (jsonRoot.has("value"))
            {
                try
                {
                    String decryptedValue = jsonRoot.get("value").getAsString();
                    EncryptedValue encryptedValue = encryptedValueService.fromString(database, decryptedValue);
                    root.setValue(encryptedValue);
                }
                catch (Exception e)
                {
                    throw new ConversionException("Failed to encrypt password - name: " + name, e);
                }
            }

            // -- last modified
            if (jsonRoot.has("lastModified"))
            {
                long lastModified = jsonRoot.get("lastModified").getAsLong();
                root.setLastModified(lastModified);
            }
        }

        // recursively parse children
        if (jsonRoot.has("children"))
        {
            for (JsonElement childElement : jsonRoot.getAsJsonArray("children"))
            {
                JsonObject child = childElement.getAsJsonObject();
                DatabaseNode childNode = root.addNew();

                importAddChildren(database, childNode, child);
            }
        }
    }

    private void exportAddChildren(Database database, DatabaseNode root, JsonObject jsonRoot) throws ConversionException
    {
        if (!root.isRoot())
        {
            String decryptedString;

            try
            {
                EncryptedValue encryptedValue = root.getValue();
                decryptedString = encryptedValueService.asString(database, encryptedValue);
            }
            catch (Exception e)
            {
                throw new ConversionException("Failed to decrypt node value - name: " + root.getName(), e);
            }

            jsonRoot.addProperty("id", root.getId());
            jsonRoot.addProperty("name", root.getName());
            jsonRoot.addProperty("value", decryptedString);
            jsonRoot.addProperty("lastModified", root.getLastModified());
        }

        // recursively add children
        DatabaseNode[] children = root.getChildren();

        if (children.length > 0)
        {
            JsonArray jsonArray = new JsonArray(children.length);
            jsonRoot.add("children", jsonArray);

            for (DatabaseNode child : root.getChildren())
            {
                JsonObject jsonChild = new JsonObject();
                jsonArray.add(jsonChild);

                exportAddChildren(database, child, jsonChild);
            }
        }
    }

}
