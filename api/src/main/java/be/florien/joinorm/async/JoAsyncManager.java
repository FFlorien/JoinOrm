package be.florien.joinorm.async;

import android.app.Activity;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by florien on 26/10/16.
 */

public class JoAsyncManager {
    private static JoAsyncManager instance;

    public static JoAsyncManager getInstance () {
        if(instance == null){
            instance = new JoAsyncManager();
        }
        return instance;
    }

    private HashMap<String, SQLiteOpenHelper> dbHelpers = new HashMap<>();
    private List<String> activitiesAlive = new ArrayList<>();

    private JoAsyncManager() {}

    public void addHelper(SQLiteOpenHelper helper, String tag) {
        dbHelpers.put(tag, helper);
    }

    public SQLiteOpenHelper getHelper(String tag) {
        return dbHelpers.get(tag);
    }

    public void activityAlive(Activity activity) {
        activitiesAlive.add(activity.getClass().getSimpleName());
    }

    public void activityDead(Activity activity) {
        activitiesAlive.remove(activity.getClass().getSimpleName());
        if (activitiesAlive.size() == 0 && dbHelpers.size() > 0) {
            for (SQLiteOpenHelper helper : dbHelpers.values()) {
                helper.close();
            }
        }
    }
}
