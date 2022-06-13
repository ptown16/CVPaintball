package org.cubeville.cvpaintball.lasertag;

import org.cubeville.cvgames.vartypes.*;

public class LaserTagTeam extends GameVariableTeam {
    public LaserTagTeam() {
        super("LaserTagTeam");
        addField("loadout-team", new GameVariableString());
        addField("tps", new GameVariableList<>(GameVariableLocation.class));
    }

    @Override
    public Object itemString() {
        return null;
    }
}
