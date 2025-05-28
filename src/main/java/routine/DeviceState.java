package routine;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

public class DeviceState implements Serializable {
    private enum DeviceStateEnum {
        CAT, NUM, LIST;
    }

    private String cat;
    private float num;
    private DeviceStateEnum type = null;
    private Map<String, DeviceState> properties = new HashMap<>();

    public DeviceState(DeviceStateEnum type) {
        this.type = type;
        if (type == DeviceStateEnum.CAT)
            cat = "val0";
        else if (type == DeviceStateEnum.NUM)
            num = 0;
    }

    public DeviceState(String val) {
        if (val.startsWith("val")) {
            type = DeviceStateEnum.CAT;
            cat = val;
        } else {
            type = DeviceStateEnum.NUM;
            num = Float.valueOf(val);
        }
    }

    public DeviceState(String val, String name) {
        properties.put(name, new DeviceState(val));
        type = DeviceStateEnum.LIST;
    }

    public DeviceState(DeviceState newDevState) {
        if (type == null)
            type = newDevState.getType();

        if (newDevState.isCat())
            cat = newDevState.getCat();
        else if (newDevState.isNum())
            num = newDevState.getNum();
        else if (newDevState.isList()) {
            if (!isList())
                type = DeviceStateEnum.LIST;
            properties.putAll(newDevState.getList());
        }
    }

    public DeviceStateEnum getType() {
        return type;
    }

    public String getCat() {
        return cat;
    }

    public float getNum() {
        return num;
    }

    public DeviceState getProperty(String name) {
        return properties.get(name);
    }

    public DeviceState putProperty(String name, DeviceState val) {
        return properties.put(name, val);
    }

    public Map<String, DeviceState> getList() {
        return properties;
    }

    public boolean isType(DeviceStateEnum type) {
        return this.type == type;
    }

    public boolean isCat() {
        return isType(DeviceStateEnum.CAT);
    }

    public boolean isNum() {
        return isType(DeviceStateEnum.NUM);
    }

    public boolean isList() {
        return isType(DeviceStateEnum.LIST);
    }

    public boolean equals(DeviceState state) {
        if (state == null)
            return false;

        if (type != state.type)
            return false;

        if (isNum())
            return this.num == state.num;
        else if (isCat())
            return this.cat.equals(state.cat);
        else if (isList()) {
            if (properties.size() != state.getList().size())
                return false;
            for (Entry<String, DeviceState> e : state.getList().entrySet()) {
                if (!properties.containsKey(e.getKey()))
                    return false;
                if (!properties.get(e.getKey()).equals(e.getValue()))
                    return false;
            }
            return true;
        }
        return false;
    }

    public boolean equalsOne(DeviceState state) {
        if (state == null)
            return false;

        if (type != state.type)
            return false;

        if (isNum())
            return this.num == state.num;
        else if (isCat())
            return this.cat.equals(state.cat);
        else if (isList()) {
            // There should only be one entry in state's list/map
            for (Entry<String, DeviceState> e : state.getList().entrySet()) {
                if (properties.containsKey(e.getKey())) {
                    return properties.get(e.getKey()).equals(e.getValue());
                }
            }
        }
        return false;
    }

    public boolean isGreaterOne(DeviceState state) {
        if (type != state.type)
            return false;
        if (isCat())
            return false;

        if (isNum())
            return this.num > state.num;
        else if (isList()) {
            // There should only be one entry in state's list/map
            for (Entry<String, DeviceState> e : state.getList().entrySet()) {
                if (properties.containsKey(e.getKey()))
                    if (e.getValue().isNum()) {
                        return properties.get(e.getKey()).getNum() > e.getValue().getNum();
                    }
            }
        }
        return false;
    }

    public boolean isLessOne(DeviceState state) {
        if (type != state.type)
            return false;
        if (isCat())
            return false;

        if (isNum())
            return this.num < state.num;
        else if (isList()) {
            // There should only be one entry in state's list/map
            for (Entry<String, DeviceState> e : state.getList().entrySet()) {
                if (properties.containsKey(e.getKey()))
                    if (e.getValue().isNum())
                        return properties.get(e.getKey()).getNum() < e.getValue().getNum();
            }
        }
        return false;
    }

    public String toString() {
        if (type == DeviceStateEnum.CAT) {
            return cat;
        } else if (type == DeviceStateEnum.NUM) {
            return String.valueOf(num);
        }
        String str = "";
        for (Entry<String, DeviceState> e : properties.entrySet()) {
            str += e.getKey() + "=" + e.getValue() + ", ";
        }
        return str.substring(0, str.length() - 2);
    }

    public boolean isFloat(String property) {

        try {
            Float.parseFloat(property);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }
}
