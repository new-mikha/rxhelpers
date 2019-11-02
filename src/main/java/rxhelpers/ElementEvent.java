/*
 *  Copyright 2019 Mikhail Karmazin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package rxhelpers;

import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class ElementEvent<T> {

    private final Object key;
    private final T element;
    private final ElementEventType eventType;
    private final long revision;


    ElementEvent(Object key, T element, ElementEventType eventType, long revision) {
        this.key = Objects.requireNonNull(key);
        this.element = element;
        this.eventType = Objects.requireNonNull(eventType);
        this.revision = revision;
    }

    @SuppressWarnings("unused")
    public Object getKey() {
        return key;
    }

    public T getElement() {
        return element;
    }

    public ElementEventType getEventType() {
        return eventType;
    }

    long getRevision() {
        return revision;
    }
}
