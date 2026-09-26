package com.mamiyaotaru.voxelmap.chunkanalysis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Fast O(n) connected-component and shape analysis for seed-solid/current-air positions. */
final class ChunkAnalysisVoidDetector {
    private ChunkAnalysisVoidDetector() { }

    static Result detect(List<Candidate> source) {
        if (source.isEmpty()) return new Result(List.of(), 0);
        Map<Long, Candidate> remaining = new HashMap<>(source.size() * 2);
        for (Candidate candidate : source) remaining.put(candidate.pos().asLong(), candidate);

        // The detector removes from remaining while traversing components, so retain a separate
        // candidate lookup for the final detected list. The input cap keeps this bounded.
        Map<Long, Candidate> all = new HashMap<>(remaining);
        RunResult thinRuns = detectThinRuns(all);
        HashSet<Long> detectedKeys = thinRuns.blocks();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        int components = thinRuns.runs();
        while (!remaining.isEmpty()) {
            long start = remaining.keySet().iterator().next();
            Candidate first = remaining.remove(start);
            List<Candidate> component = new ArrayList<>();
            component.add(first);
            queue.add(start);
            while (!queue.isEmpty()) {
                long packed = queue.removeFirst();
                for (Direction direction : Direction.values()) {
                    long neighborKey = BlockPos.offset(packed, direction);
                    Candidate neighbor = remaining.remove(neighborKey);
                    if (neighbor != null) {
                        component.add(neighbor);
                        queue.addLast(neighborKey);
                    }
                }
            }
            if (isPlayerShaped(component)) {
                components++;
                component.forEach(candidate -> detectedKeys.add(candidate.pos().asLong()));
            }
        }
        List<Candidate> detected = new ArrayList<>(detectedKeys.size());
        detectedKeys.forEach(key -> detected.add(all.get(key)));
        return new Result(List.copyOf(detected), components);
    }

    private static RunResult detectThinRuns(Map<Long, Candidate> candidates) {
        HashSet<Long> detected = new HashSet<>();
        int detectedRuns = 0;
        for (Candidate candidate : candidates.values()) {
            long start = candidate.pos().asLong();
            for (Direction.Axis axis : Direction.Axis.values()) {
                Direction negative = Direction.get(Direction.AxisDirection.NEGATIVE, axis);
                Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, axis);
                if (candidates.containsKey(BlockPos.offset(start, negative))) continue;
                ArrayList<Long> run = new ArrayList<>();
                int perpendicularNeighbors = 0;
                int unreliable = 0;
                long cursor = start;
                Candidate current;
                while ((current = candidates.get(cursor)) != null) {
                    run.add(cursor);
                    if (current.unreliableBaseline()) unreliable++;
                    for (Direction direction : Direction.values()) {
                        if (direction.getAxis() != axis && candidates.containsKey(BlockPos.offset(cursor, direction))) {
                            perpendicularNeighbors++;
                        }
                    }
                    cursor = BlockPos.offset(cursor, positive);
                }
                boolean unreliableBaseline = unreliable * 4 >= run.size();
                int minimumLength = unreliableBaseline ? 6 : 4;
                int maximumSideContacts = run.size() * (unreliableBaseline ? 1 : 2);
                if (run.size() >= minimumLength && perpendicularNeighbors <= maximumSideContacts) {
                    if (detected.addAll(run)) detectedRuns++;
                }
            }
        }
        return new RunResult(detected, detectedRuns);
    }

    private static boolean isPlayerShaped(List<Candidate> component) {
        int size = component.size();
        if (size < 4) return false;
        int minX=Integer.MAX_VALUE, minY=Integer.MAX_VALUE, minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE, maxY=Integer.MIN_VALUE, maxZ=Integer.MIN_VALUE;
        double sumX=0, sumY=0, sumZ=0;
        for (Candidate candidate : component) {
            BlockPos pos = candidate.pos();
            minX=Math.min(minX,pos.getX()); maxX=Math.max(maxX,pos.getX());
            minY=Math.min(minY,pos.getY()); maxY=Math.max(maxY,pos.getY());
            minZ=Math.min(minZ,pos.getZ()); maxZ=Math.max(maxZ,pos.getZ());
            sumX+=pos.getX(); sumY+=pos.getY(); sumZ+=pos.getZ();
        }
        int sx=maxX-minX+1, sy=maxY-minY+1, sz=maxZ-minZ+1;
        int longest=Math.max(sx,Math.max(sy,sz));
        int shortest=Math.min(sx,Math.min(sy,sz));
        int middle=sx+sy+sz-longest-shortest;
        double volume=(double)sx*sy*sz;
        double density=size/volume;
        long unreliable = component.stream().filter(Candidate::unreliableBaseline).count();
        boolean baselineReliable = unreliable * 4 < size;

        boolean shaft=sy>=4 && sx<=3 && sz<=3 && density>=0.30D;
        boolean corridor=longest>=5 && middle<=4 && density>=0.24D;
        boolean pitOrRoom=baselineReliable && size>=6 && density>=0.50D
                && ((sx>=2&&sz>=2)||(sx>=2&&sy>=2)||(sz>=2&&sy>=2));
        boolean excavation=baselineReliable && size>=32 && density>=0.45D;
        boolean stairs=size>=5 && sy>=2 && Math.max(sx,sz)>=3 && stairCorrelation(component,sumX/size,sumY/size,sumZ/size)>=0.62D;
        return shaft || corridor || pitOrRoom || excavation || stairs;
    }

    private static double stairCorrelation(List<Candidate> component, double meanX, double meanY, double meanZ) {
        double covX=0,covZ=0,varX=0,varY=0,varZ=0;
        for (Candidate candidate : component) {
            BlockPos pos=candidate.pos();
            double x=pos.getX()-meanX,y=pos.getY()-meanY,z=pos.getZ()-meanZ;
            covX+=x*y; covZ+=z*y; varX+=x*x; varY+=y*y; varZ+=z*z;
        }
        if (varY==0.0D) return 0.0D;
        double xScore=varX==0.0D?0.0D:Math.abs(covX)/Math.sqrt(varX*varY);
        double zScore=varZ==0.0D?0.0D:Math.abs(covZ)/Math.sqrt(varZ*varY);
        return Math.max(xScore,zScore);
    }

    record Candidate(BlockPos pos, BlockState expectedState, boolean unreliableBaseline) { }
    private record RunResult(HashSet<Long> blocks, int runs) { }
    record Result(List<Candidate> blocks, int components) { }
}
