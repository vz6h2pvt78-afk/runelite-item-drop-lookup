package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

/** Validate each optional drop enrichment independently and withhold duplicate keys. */
@Slf4j
final class ReviewedRows
{
    static <T> Map<String,T> read(Gson gson, String resource, String collection, String keyField, BiFunction<JsonObject,String,T> parse)
    {
        Map<String,T> records=new HashMap<>();
        try (InputStream input=ReviewedRows.class.getResourceAsStream(resource))
        {
            if (input==null) return records;
            JsonObject root=gson.fromJson(new InputStreamReader(input,StandardCharsets.UTF_8),JsonObject.class);
            fields(root,"schemaVersion,"+collection,"");
            integer(root,"schemaVersion",1,1);
            Set<String> seen=new HashSet<>();
            for (JsonElement value : array(root,collection))
            {
                try
                {
                    JsonObject row=object(value);
                    String key=string(row,keyField);
                    if (seen.add(key)) records.put(key,parse.apply(row,key));
                    else records.remove(key);
                }
                catch (RuntimeException ex)
                {
                    log.debug("Withholding an unverified row in {}",resource,ex);
                }
            }
        }
        catch (Exception ex)
        {
            log.debug("Optional resource unavailable: {}",resource,ex);
        }
        return records;
    }
}
