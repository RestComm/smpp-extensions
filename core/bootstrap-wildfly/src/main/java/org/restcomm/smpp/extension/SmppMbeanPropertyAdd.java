package org.restcomm.smpp.extension;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.dmr.ModelNode;

import static org.restcomm.smpp.extension.SmppMbeanPropertyDefinition.PROPERTY_ATTRIBUTES;

class SmppMbeanPropertyAdd extends AbstractAddStepHandler {

    public static final SmppMbeanPropertyAdd INSTANCE = new SmppMbeanPropertyAdd();

    private SmppMbeanPropertyAdd() {
    }

    @Override
    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        SmppMbeanPropertyDefinition.NAME_ATTR.validateAndSet(operation, model);
        for (SimpleAttributeDefinition def : PROPERTY_ATTRIBUTES) {
            def.validateAndSet(operation, model);
        }
    }
}