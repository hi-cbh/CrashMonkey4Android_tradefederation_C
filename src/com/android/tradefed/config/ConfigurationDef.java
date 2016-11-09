/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tradefed.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds a record of a configuration, its associated objects and their options.
 */
public class ConfigurationDef {

    /** a map of object type names to config object class name(s). */
    private final Map<String, List<String>> mObjectClassMap;
    /** a list of option name/value pairs. */
    private final List<OptionDef> mOptionList;
    /** a cache of the frequency of every classname */
    private final Map<String, Integer> mClassFrequency;

    static class OptionDef {
        final String name;
        final String key;
        final String value;

        OptionDef(String optionName, String optionValue) {
            this(optionName, null, optionValue);
        }

        OptionDef(String optionName, String optionKey, String optionValue) {
            this.name = optionName;
            this.key = optionKey;
            this.value = optionValue;
        }
    }

    /** the unique name of the configuration definition */
    private final String mName;

    /** a short description of the configuration definition */
    private String mDescription = "";

    public ConfigurationDef(String name) {
        mName = name;
        // use LinkedHashMap to keep objects in same order they were added.
        mObjectClassMap = new LinkedHashMap<String, List<String>>();
        mOptionList = new ArrayList<OptionDef>();
        mClassFrequency = new HashMap<String, Integer>();
    }

    /**
     * Returns a short description of the configuration
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Sets the configuration definition description
     */
    void setDescription(String description) {
        mDescription = description;
    }

    /**
     * Adds a config object to the definition
     *
     * @param typeName the config object type name
     * @param className the class name of the config object
     * @return the number of times this className has appeared in this {@link ConfigurationDef},
     *         including this time.  Because all {@link ConfigurationDef} methods return these
     *         classes with a constant ordering, this index can serve as a unique identifier for the
     *         just-added instance of <code>clasName</code>.
     */
    int addConfigObjectDef(String typeName, String className) {
        List<String> classList = mObjectClassMap.get(typeName);
        if (classList == null) {
            classList = new ArrayList<String>();
            mObjectClassMap.put(typeName, classList);
        }
        classList.add(className);

        // Increment and store count for this className
        Integer freq = mClassFrequency.get(className);
        freq = freq == null ? 1 : freq + 1;
        mClassFrequency.put(className, freq);

        return freq;
    }

    /**
     * Adds option to the definition
     *
     * @param optionName the name of the option
     * @param optionValue the option value
     */
    void addOptionDef(String optionName, String optionKey, String optionValue) {
        if (optionKey == null) {
            mOptionList.add(new OptionDef(optionName, optionValue));
        } else {
            mOptionList.add(new OptionDef(optionName, optionKey, optionValue));
        }
    }

    /**
     * Get the object type name-class map.
     * <p/>
     * Exposed for unit testing
     */
    Map<String, List<String>> getObjectClassMap() {
        return mObjectClassMap;
    }

    /**
     * Get the option name-value map.
     * <p/>
     * Exposed for unit testing
     */
    List<OptionDef> getOptionList() {
        return mOptionList;
    }

    /**
     * Creates a configuration from the info stored in this definition, and populates its fields
     * with the provided option values.
     *
     * @return the created {@link IConfiguration}
     * @throws ConfigurationException if configuration could not be created
     */
    IConfiguration createConfiguration() throws ConfigurationException {
        IConfiguration config = new Configuration(getName(), getDescription());

        for (Map.Entry<String, List<String>> objClassEntry : mObjectClassMap.entrySet()) {
            List<Object> objectList = new ArrayList<Object>(objClassEntry.getValue().size());
            for (String className : objClassEntry.getValue()) {
                Object configObject = createObject(objClassEntry.getKey(), className);
                objectList.add(configObject);
            }
            config.setConfigurationObjectList(objClassEntry.getKey(), objectList);
        }
        for (OptionDef optionEntry : mOptionList) {
            if (optionEntry.key == null) {
                config.injectOptionValue(optionEntry.name, optionEntry.value);
            } else {
                config.injectOptionValue(optionEntry.name, optionEntry.key, optionEntry.value);
            }
        }

        return config;
    }

    /**
     * Creates a global configuration from the info stored in this definition, and populates its
     * fields with the provided option values.
     *
     * @return the created {@link IGlobalConfiguration}
     * @throws ConfigurationException if configuration could not be created
     */
    IGlobalConfiguration createGlobalConfiguration() throws ConfigurationException {
        IGlobalConfiguration config = new GlobalConfiguration(getName(), getDescription());
        //
        for (Map.Entry<String, List<String>> objClassEntry : mObjectClassMap.entrySet()) {
            List<Object> objectList = new ArrayList<Object>(objClassEntry.getValue().size());
            for (String className : objClassEntry.getValue()) {
                Object configObject = createObject(objClassEntry.getKey(), className);
                objectList.add(configObject);
            }
            config.setConfigurationObjectList(objClassEntry.getKey(), objectList);
        }
        for (OptionDef optionEntry : mOptionList) {
            if (optionEntry.key == null) {
                config.injectOptionValue(optionEntry.name, optionEntry.value);
            } else {
                config.injectOptionValue(optionEntry.name, optionEntry.key, optionEntry.value);
            }
        }

        return config;
    }

    /**
     * Gets the name of this configuration definition
     *
     * @return
     */
    public String getName() {
        return mName;
    }

    /**
     * Creates a config object associated with this definition.
     *
     * @param objectTypeName the name of the object. Used to generate more descriptive error
     *            messages
     * @param className the class name of the object to load
     * @return the config object
     * @throws ConfigurationException if config object could not be created
     */
    private Object createObject(String objectTypeName, String className)
            throws ConfigurationException {
        try {
            Class<?> objectClass = getClassForObject(objectTypeName, className);
            Object configObject = objectClass.newInstance();
            return configObject;
        } catch (InstantiationException e) {
            throw new ConfigurationException(String.format(
                    "Could not instantiate class %s for config object type %s", className,
                    objectTypeName), e);
        } catch (IllegalAccessException e) {
            throw new ConfigurationException(String.format(
                    "Could not access class %s for config object type %s", className,
                    objectTypeName), e);
        }
    }

    /**
     * Loads the class for the given the config object associated with this definition.
     *
     * @param objectTypeName the name of the config object type. Used to generate more descriptive
     *            error messages
     * @param className the class name of the object to load
     * @return the config object populated with default option values
     * @throws ConfigurationException if config object could not be created
     */
    private Class<?> getClassForObject(String objectTypeName, String className)
            throws ConfigurationException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(
                    String.format("Could not find class %s for config object type %s", className,
                            objectTypeName), e);
        }
    }

    /**
     * Add a included ConfigurationDef to this.
     *
     * @param includedDef
     */
    void includeConfigDef(ConfigurationDef includedDef) {
        for (Map.Entry<String, List<String>> mapEntry :
                includedDef.getObjectClassMap().entrySet()) {
            for (String configClass : mapEntry.getValue()) {
                addConfigObjectDef(mapEntry.getKey(), configClass);
            }
        }
        mOptionList.addAll(includedDef.getOptionList());
    }
}
