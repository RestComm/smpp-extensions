package org.restcomm.smpp.extension;

import org.jboss.as.controller.AbstractRemoveStepHandler;

class SmppMbeanPropertyRemove extends AbstractRemoveStepHandler {

    public static final SmppMbeanPropertyRemove INSTANCE = new SmppMbeanPropertyRemove();

    private SmppMbeanPropertyRemove() {
    }
}