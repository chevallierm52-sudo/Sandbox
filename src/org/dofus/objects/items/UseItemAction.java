package org.dofus.objects.items;

/**
 * Action serveur declenchee par l'utilisation d'un template d'objet.
 * Table source : use_item_actions(template, type, args).
 */
public class UseItemAction {

    private final int templateId;
    private final int type;
    private final String args;

    public UseItemAction(int templateId, int type, String args) {
        this.templateId = templateId;
        this.type = type;
        this.args = args != null ? args : "";
    }

    public int getTemplateId() { return templateId; }
    public int getType() { return type; }
    public String getArgs() { return args; }

    public String[] splitArgs() {
        return args.isEmpty() ? new String[0] : args.split(",");
    }

    @Override
    public String toString() {
        return "UseItemAction{template=" + templateId + ", type=" + type + ", args='" + args + "'}";
    }
}
