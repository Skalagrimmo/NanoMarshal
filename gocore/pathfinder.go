// Package nanocore contains platform-independent simulation primitives for NanoMarshal.
package nanocore

import (
	"container/heap"
	"math"
)

const maxSearchIterations = 350

var (
	neighborDX = [...]int32{0, 0, 1, -1, 1, 1, -1, -1}
	neighborDY = [...]int32{1, -1, 0, 0, 1, -1, 1, -1}
	diagonalCost float32 = 1.414
)

type searchNode struct {
	cell int
	gCost float32
	hCost float32
	seq int
}

func (n *searchNode) fCost() float32 { return n.gCost + n.hCost }

type nodeHeap []*searchNode
func (h nodeHeap) Len() int { return len(h) }
func (h nodeHeap) Less(i, j int) bool {
	fi, fj := h[i].fCost(), h[j].fCost()
	if fi == fj { return h[i].seq < h[j].seq }
	return fi < fj
}
func (h nodeHeap) Swap(i, j int) { h[i], h[j] = h[j], h[i] }
func (h *nodeHeap) Push(x any) { *h = append(*h, x.(*searchNode)) }
func (h *nodeHeap) Pop() any {
	old := *h
	n := len(old)
	item := old[n-1]
	old[n-1] = nil
	*h = old[:n-1]
	return item
}

// FindPath runs eight-direction A* on a row-major grid of traversal costs.
// costs[y*width+x] <= 0 or non-finite means blocked; positive values are costs.
// The result is flattened x,y pairs, including the start and target cells.
// An empty result indicates malformed input. If no route is found in the
// historical search budget, the target cell alone is returned to preserve the
// existing Kotlin pathfinder's fallback behavior.
func FindPath(width, height, startX, startY, targetX, targetY int32, costs []float32) []int32 {
	if width <= 0 || height <= 0 || int64(width)*int64(height) != int64(len(costs)) { return nil }
	startX, startY = clamp(startX, 0, width-1), clamp(startY, 0, height-1)
	targetX, targetY = clamp(targetX, 0, width-1), clamp(targetY, 0, height-1)
	index := func(x, y int32) int { return int(y*width + x) }
	isOpen := func(x, y int32) bool {
		if x < 0 || y < 0 || x >= width || y >= height { return false }
		v := costs[index(x, y)]
		return v > 0 && !invalid(v)
	}
	if !isOpen(targetX, targetY) {
		bestX, bestY := int32(-1), int32(-1)
		bestDistance := int64(math.MaxInt64)
		for i := range neighborDX {
			nx, ny := targetX+neighborDX[i], targetY+neighborDY[i]
			if !isOpen(nx, ny) { continue }
			dx, dy := int64(nx-startX), int64(ny-startY)
			d := dx*dx + dy*dy
			if d < bestDistance { bestDistance, bestX, bestY = d, nx, ny }
		}
		if bestX >= 0 { targetX, targetY = bestX, bestY }
	}
	startCell, targetCell := index(startX, startY), index(targetX, targetY)
	if startCell == targetCell { return []int32{startX, startY} }

	count := int(width*height)
	gScore := make([]float32, count)
	parents := make([]int, count)
	closed := make([]bool, count)
	for i := range gScore { gScore[i] = math.MaxFloat32; parents[i] = -1 }
	open := &nodeHeap{}
	heap.Init(open)
	sequence := 0
	gScore[startCell] = 0
	heap.Push(open, &searchNode{cell:startCell, gCost:0, hCost:heuristic(startX,startY,targetX,targetY), seq:sequence})

	iterations := 0
	for open.Len() > 0 && iterations < maxSearchIterations {
		iterations++
		current := heap.Pop(open).(*searchNode)
		if closed[current.cell] { continue }
		if current.cell == targetCell { return reconstruct(current.cell, parents, width) }
		closed[current.cell] = true
		cx, cy := int32(current.cell%int(width)), int32(current.cell/int(width))
		for i := range neighborDX {
			nx, ny := cx+neighborDX[i], cy+neighborDY[i]
			if !isOpen(nx,ny) { continue }
			nCell := index(nx,ny)
			if closed[nCell] { continue }
			step := float32(1)
			if neighborDX[i] != 0 && neighborDY[i] != 0 { step = diagonalCost }
			newG := current.gCost + step*costs[nCell]
			if newG >= gScore[nCell] { continue }
			gScore[nCell], parents[nCell] = newG, current.cell
			sequence++
			heap.Push(open, &searchNode{cell:nCell, gCost:newG, hCost:heuristic(nx,ny,targetX,targetY), seq:sequence})
		}
	}
	return []int32{targetX,targetY}
}

func clamp(v, low, high int32) int32 {
	if v < low { return low }
	if v > high { return high }
	return v
}
func invalid(v float32) bool { return math.IsNaN(float64(v)) || math.IsInf(float64(v), 0) }
func abs32(v int32) int32 { if v < 0 { return -v }; return v }
func heuristic(x1,y1,x2,y2 int32) float32 {
	dx,dy := abs32(x1-x2),abs32(y1-y2)
	m := dx
	if dy < m { m = dy }
	return float32(dx+dy) + (diagonalCost-2)*float32(m)
}
func reconstruct(target int, parents []int, width int32) []int32 {
	reversed := make([]int32,0,32)
	for cell := target; cell >= 0; cell = parents[cell] {
		reversed = append(reversed,int32(cell%int(width)),int32(cell/int(width)))
		if parents[cell] == -1 { break }
	}
	out := make([]int32,len(reversed))
	pairs := len(reversed)/2
	for i:=0;i<pairs;i++ { out[i*2]=reversed[(pairs-1-i)*2]; out[i*2+1]=reversed[(pairs-1-i)*2+1] }
	return out
}
