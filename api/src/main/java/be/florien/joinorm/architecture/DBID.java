package be.florien.joinorm.architecture;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by FlamentF on 29-09-16.
 */

class DbId {
    private List<String> ids;

    DbId() {
        ids = new ArrayList<>(0);
    }

    DbId(int... intIds) {
        ids = new ArrayList<>(intIds.length);
        for (int id : intIds) {
            this.ids.add(String.valueOf(id));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DbId) {
            DbId otherDbId = (DbId) o;
            if (otherDbId.ids == null && ids == null) {
                return true;
            } else if (otherDbId.ids == null || ids == null) {
                return false;
            }
            if (otherDbId.ids.size() != ids.size()) {
                return false;
            }


            for (int i = 0; i < ids.size() ; i++) {
                if (!ids.get(i).equals(otherDbId.ids.get(i))) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    public List<String> getIds() {
        return ids;
    }
}
