package org.example.jdbc.internal;

import net.ttddyy.dsproxy.proxy.ParameterSetOperation;
import org.example.model.QueryParam;

import java.util.ArrayList;
import java.util.List;


public final class PlainParamMapper {

    private PlainParamMapper() {
    }

    public static List<List<QueryParam>> toParamSets(List<List<ParameterSetOperation>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        List<List<QueryParam>> result = new ArrayList<>(source.size());

        for (List<ParameterSetOperation> paramSet : source) {
            if (paramSet == null || paramSet.isEmpty()) {
                result.add(List.of());
                continue;
            }

            List<QueryParam> params = new ArrayList<>(paramSet.size());
            for (ParameterSetOperation op : paramSet) {
                Object[] args = op.getArgs();
                if (args != null && args.length >= 2 && args[0] instanceof Integer index) {
                    params.add(new QueryParam(index, args[1]));
                }
            }
            result.add(List.copyOf(params));
        }

        return List.copyOf(result);
    }
}
