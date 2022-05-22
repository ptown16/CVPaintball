package org.cubeville.cvpaintball.lasertag;

import org.cubeville.cvgames.vartypes.*;

public class LaserTagTeam extends GameVariableObject {
    public LaserTagTeam() {
        super("PaintballTeam");
        addField("name", new GameVariableString());
        addField("chat-color", new GameVariableChatColor());
        addField("loadout-team", new GameVariableString());
        addField("tps", new GameVariableList<>(GameVariableLocation.class));
    }

    @Override
    public Object itemString() {
        return null;
    }
}
