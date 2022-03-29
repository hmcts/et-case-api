package uk.gov.hmcts.reform.et.syaapi.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.SneakyThrows;

public final class ResourceLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
        .registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private ResourceLoader() {
        // Utility class
    }

    @SneakyThrows
    public static <T> T fromString(String jsonFileName, Class<T> clazz) {
        String json = ResourceUtil.resourceAsString(jsonFileName);
        return OBJECT_MAPPER.readValue(json, clazz);
    }
}
