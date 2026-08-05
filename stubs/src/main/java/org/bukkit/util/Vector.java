package org.bukkit.util;
public class Vector implements Cloneable {
 private double x, y, z;
 public Vector() {}
 public Vector(double x, double y, double z) { this.x=x; this.y=y; this.z=z; }
 public Vector clone() { return new Vector(x,y,z); }
 public Vector multiply(double m) { x*=m; y*=m; z*=m; return this; }
 public Vector add(Vector v) { x+=v.x; y+=v.y; z+=v.z; return this; }
 public Vector subtract(Vector v) { x-=v.x; y-=v.y; z-=v.z; return this; }
 public Vector normalize() { double l=length(); if(l>0){x/=l;y/=l;z/=l;} return this; }
 public double length() { return Math.sqrt(lengthSquared()); }
 public double lengthSquared() { return x*x+y*y+z*z; }
 public double dot(Vector v) { return x*v.x+y*v.y+z*v.z; }
 public Vector crossProduct(Vector o) { double nx=y*o.z-z*o.y; double ny=z*o.x-x*o.z; double nz=x*o.y-y*o.x; x=nx;y=ny;z=nz; return this; }
 public double getY() { return y; }
 public double getX() { return x; }
 public double getZ() { return z; }
}
