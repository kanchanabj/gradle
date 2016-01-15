/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract

import spock.lang.Specification
import spock.lang.Unroll

class PropertyAccessorTypeTest extends Specification {
    @Unroll
    def "method names #getGetterName, #isGetterName and #setterName extract to property name '#propertyName'"() {
        expect:
        PropertyAccessorType.GET_GETTER.propertyNameFor(getGetterName) == propertyName
        PropertyAccessorType.IS_GETTER.propertyNameFor(isGetterName) == propertyName
        PropertyAccessorType.SETTER.propertyNameFor(setterName) == propertyName

        where:
        getGetterName    | isGetterName    | setterName       | propertyName
        "getUrl"         | "isUrl"         | "setUrl"         | "url"
        "getURL"         | "isURL"         | "setURL"         | "URL"
        "getcCompiler"   | "iscCompiler"   | "setcCompiler"   | "cCompiler"
        "getCCompiler"   | "isCCompiler"   | "setCCompiler"   | "CCompiler"
        "getCppCompiler" | "isCppCompiler" | "setCppCompiler" | "cppCompiler"
        "getCPPCompiler" | "isCPPCompiler" | "setCPPCompiler" | "CPPCompiler"
    }

    static class Bean {
        private String myurl, myURL, mycCompiler, myCCompilerField, mycppCompilerField, myCPPCompilerField
        String getUrl() { myurl }
        void setUrl(String value) { myurl = value }
        String getURL() { myURL }
        void setURL(String value) { myURL = value }
        String getcCompiler() { mycCompiler }
        void setcCompiler(String value) { mycCompiler = value }
        String getCCompiler() { myCCompilerField }
        void setCCompiler(String value) { myCCompilerField = value }
        String getCppCompiler() { mycppCompilerField }
        void setCppCompiler(String value) { mycppCompilerField = value }
        String getCPPCompiler() { myCPPCompilerField }
        void setCPPCompiler(String value) { myCPPCompilerField = value }
    }

    def "property extraction is on par with groovy properties"() {
        given:
        def bean = new Bean()

        when:
        // Exercise setters
        bean.url = 'lower-case'
        bean.URL = 'upper-case'
        bean.cCompiler = 'lower-case first char'
        bean.CCompiler = 'upper-case first char'
        bean.cppCompiler = 'cppCompiler'
        bean.CPPCompiler = 'CPPCompiler'

        then:
        // Exercise getters
        bean.url == 'lower-case' && bean.getUrl() == bean.url
        bean.URL == 'upper-case' && bean.getURL() == bean.URL
        bean.cCompiler == 'lower-case first char' && bean.getcCompiler() == bean.cCompiler
        bean.CCompiler == 'upper-case first char' && bean.getCCompiler() == bean.CCompiler
        bean.cppCompiler == 'cppCompiler' && bean.getCppCompiler() == bean.cppCompiler
        bean.CPPCompiler == 'CPPCompiler' && bean.getCPPCompiler() == bean.CPPCompiler
    }

    static class DeviantBean {
        String gettingStarted() {
            'Getting started!'
        }
        boolean isidore() {
            true
        }
        void settings(String value) {}
    }

    def "deviant bean properties are not considered as such by Gradle"() {
        expect:
        !PropertyAccessorType.isGetterName('gettingStarted')
        !PropertyAccessorType.isGetterName('isidore')
        !PropertyAccessorType.isSetterName('settings')
    }

    def "deviant bean properties are considered as such by Groovy"() {
        when:
        def bean = new DeviantBean()
        bean.tings = 'Some settings'

        then:
        bean.tingStarted == 'Getting started!'
        bean.idore == true
    }
}

