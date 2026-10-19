package nanocore

import (
	"math"
	"testing"
)

func costs(w,h int,v float32) []float32 {
	c := make([]float32,w*h)
	for i := range c { c[i] = v }
	return c
}
func pairs(p []int32) [][2]int32 {
	out := make([][2]int32,0,len(p)/2)
	for i:=0;i+1<len(p);i+=2 { out=append(out,[2]int32{p[i],p[i+1]}) }
	return out
}

func TestFindPathOpenGrid(t *testing.T) {
	const w,h = 8,6
	p := pairs(FindPath(w,h,0,0,7,5,costs(w,h,1)))
	if len(p)<2 || p[0] != [2]int32{0,0} || p[len(p)-1] != [2]int32{7,5} { t.Fatalf("bad route: %v",p) }
	for i:=1;i<len(p);i++ {
		dx,dy:=abs32(p[i][0]-p[i-1][0]),abs32(p[i][1]-p[i-1][1])
		if dx>1 || dy>1 || (dx==0 && dy==0) { t.Fatalf("bad step %v -> %v",p[i-1],p[i]) }
	}
}
func TestFindPathDetoursAroundWall(t *testing.T) {
	const w,h=7,6
	c:=costs(w,h,1)
	for y:=0;y<h-1;y++ { c[y*w+3]=0 }
	p:=pairs(FindPath(w,h,1,1,5,1,c))
	if len(p)==1 { t.Fatalf("expected detour, got %v",p) }
	for _,v:=range p { if v[0]==3 && v[1]<h-1 { t.Fatalf("crossed blocked wall at %v",v) } }
}
func TestFindPathAvoidsHighCostCells(t *testing.T) {
	const w,h=7,5
	c:=costs(w,h,1)
	for x:=1;x<6;x++ { c[2*w+x]=100 }
	p:=pairs(FindPath(w,h,0,2,6,2,c))
	for _,v:=range p { if v[1]==2 && v[0]>0 && v[0]<6 { t.Fatalf("used expensive cell %v",v) } }
}
func TestFindPathSameStartAndTarget(t *testing.T) {
	p:=FindPath(3,3,1,1,1,1,costs(3,3,1))
	if len(p)!=2 || p[0]!=1 || p[1]!=1 { t.Fatalf("unexpected path %v",p) }
}
func TestFindPathClampsCoordinates(t *testing.T) {
	p:=pairs(FindPath(4,4,-4,-2,99,12,costs(4,4,1)))
	if len(p)<2 || p[0]!=[2]int32{0,0} || p[len(p)-1]!=[2]int32{3,3} { t.Fatalf("bad clamped route: %v",p) }
}
func TestFindPathUnreachableFallsBackToTarget(t *testing.T) {
	const w,h=5,5
	c:=costs(w,h,0)
	c[0]=1; c[4*w+4]=1
	p:=FindPath(w,h,0,0,4,4,c)
	if len(p)!=2 || p[0]!=4 || p[1]!=4 { t.Fatalf("bad fallback: %v",p) }
}
func TestFindPathRejectsMalformedGridAndBlocksNaN(t *testing.T) {
	if got:=FindPath(4,4,0,0,3,3,costs(4,4,1)[:3]); len(got)!=0 { t.Fatalf("expected nil, got %v",got) }
	c:=costs(3,3,1); c[4]=float32(math.NaN())
	if got:=FindPath(3,3,0,0,2,2,c); len(got)==0 { t.Fatal("a route should remain around NaN cell") }
}
