/*
 * Copyright (c) 2011 JME2Loader
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'JME2Loader' nor the names of its contributors 
 *   may be used to endorse or promote products derived from this software 
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.skyebook.jme2loader;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.logging.Logger;

import com.jme.scene.Geometry;
import com.jme.scene.Node;
import com.jme.scene.Spatial;
import com.jme.scene.TriMesh;
import com.jme.scene.state.BlendState;
import com.jme.scene.state.MaterialState;
import com.jme.scene.state.RenderState.StateType;
import com.jme.scene.state.TextureState;
import com.jme.system.dummy.DummySystemProvider;
import com.jme.util.export.binary.BinaryImporter;
import com.jme.util.export.xml.XMLImporter;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

/**
 * 
 */

/**
 * Only supports TriMesh
 * jME3 geometry is what gets added to a scene (it contains a mesh object)
 * @author Skye Book
 *
 */
public class JME2Loader implements AssetLoader{

	private static final Logger logger = Logger.getLogger(JME2Loader.class.getName());

	private static ArrayList<com.jme.image.Image.Format> unsupportedImageFormat;
	static{
		unsupportedImageFormat = new ArrayList<com.jme.image.Image.Format>();
		for(com.jme.image.Image.Format format : com.jme.image.Image.Format.values()){
			try{
				if(Image.Format.valueOf(format.name())==null){
					unsupportedImageFormat.add(format);
				}
			}catch (IllegalArgumentException e) {
				unsupportedImageFormat.add(format);
			}
		}
	}

	public static com.jme3.scene.Spatial twoToThree(Spatial spatial, AssetManager assetManager){
		if(spatial instanceof Node){
			com.jme3.scene.Node node = new com.jme3.scene.Node(spatial.getName());
			if(((Node)spatial).getQuantity()>0){
				for(Spatial child : ((Node)spatial).getChildren()){
					node.attachChild(twoToThree(child, assetManager));
				}
			}
			// do translation/rotation/scale
			transferTranslationScaleRotation(node, spatial);
			return node;
		}
		else if(spatial instanceof Geometry){
			Mesh mesh = new Mesh();

			// Handle Geometry

			// transfer vertex buffer
			FloatBuffer vertexBuffer = ((Geometry)spatial).getVertexBuffer().duplicate();
			vertexBuffer.rewind();
			mesh.setBuffer(Type.Position, 3, vertexBuffer);

			// transfer normal buffer if it exists
			FloatBuffer normalBuffer = ((Geometry)spatial).getNormalBuffer();
			if(normalBuffer!=null){
				normalBuffer = normalBuffer.duplicate();
				normalBuffer.rewind();
				mesh.setBuffer(Type.Normal, 3, normalBuffer);
			}

			// transfer color buffer if it exists
			FloatBuffer colorBuffer = ((Geometry)spatial).getColorBuffer();
			if(colorBuffer!=null){
				colorBuffer = colorBuffer.duplicate();
				colorBuffer.rewind();
				mesh.setBuffer(Type.Color, 4, colorBuffer);
			}

			if(spatial instanceof TriMesh){
				// transfer index buffer
				IntBuffer indexBuffer = ((TriMesh)spatial).getIndexBuffer().duplicate();
				indexBuffer.rewind();
				mesh.setBuffer(Type.Index, 3, indexBuffer);
			}

			// replicate the model bounds
			if(((Geometry)spatial).getModelBound()!=null){
				if(((Geometry)spatial).getModelBound() instanceof com.jme.bounding.BoundingBox){
					mesh.setBound(new com.jme3.bounding.BoundingBox());
				}
				else if(((Geometry)spatial).getModelBound() instanceof com.jme.bounding.BoundingSphere){
					mesh.setBound(new com.jme3.bounding.BoundingSphere());
				}
				mesh.updateBound();
			}
			else{
				logger.info("No BoundingVolume found, defaulting to a BoundingBox");
				mesh.setBound(new com.jme3.bounding.BoundingBox());
				mesh.updateBound();
			}


			com.jme3.scene.Geometry geometry = new com.jme3.scene.Geometry(spatial.getName());
			geometry.setMesh(mesh);

			Material mat;

			// Handle Materials
			if(spatial.getRenderState(StateType.Material)!=null){
				mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
				MaterialState ms = (MaterialState)spatial.getRenderState(StateType.Material);
				mat.setColor("Diffuse", twoToThree(ms.getDiffuse()));
				mat.setColor("Ambient", twoToThree(ms.getAmbient()));
				mat.setColor("Specular", twoToThree(ms.getSpecular()));
				mat.setFloat("Shininess", ms.getShininess());
				mat.setTransparent(true);
				mat.getAdditionalRenderState().setAlphaTest(true);
				mat.setBoolean("UseMaterialColors" , true);
				mat.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);

				if(spatial.getRenderState(StateType.Texture)!=null){
					Texture texture = twoToThree(((TextureState)spatial.getRenderState(StateType.Texture)).getTexture());
					if(texture!=null) mat.setTexture("DiffuseMap", texture);
				}
				/*
				if(spatial.getRenderState(StateType.Texture)!=null){
					if(((TextureState)spatial.getRenderState(StateType.Texture)).getTexture()!=null){
						Texture texture = twoToThree(((TextureState)spatial.getRenderState(StateType.Texture)).getTexture());
						if(texture!=null) mat.setTexture("DiffuseMap", texture);
					}
					else{
						System.out.println("Hey, what's going on here?!  The texture on some texture state is null");
					}
				}
				 */
			}
			else{
				mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
				mat.setColor("Color", ColorRGBA.Blue);
			}

			// Blend modes
			if(spatial.getRenderState(StateType.Blend)!=null){
				BlendState blendState = ((BlendState)spatial.getRenderState(StateType.Blend));
			}


			if(spatial.getRenderState(StateType.Wireframe)!=null){
				mat.getAdditionalRenderState().setWireframe(true);
			}

			geometry.setMaterial(mat);

			// do translation/rotation/scale
			transferTranslationScaleRotation(geometry, spatial);

			return geometry;
		}
		else{
			logger.warning("Unrecognized type");
			return null;
		}
	}



	private static void transferTranslationScaleRotation(com.jme3.scene.Spatial newSpatial, com.jme.scene.Spatial oldSpatial){
		newSpatial.setLocalTranslation(twoToThree(oldSpatial.getLocalTranslation()));
		newSpatial.setLocalRotation(twoToThree(oldSpatial.getLocalRotation()));
		newSpatial.setLocalScale(twoToThree(oldSpatial.getLocalScale()));
	}

	public static com.jme3.math.ColorRGBA twoToThree(com.jme.renderer.ColorRGBA color){
		return new com.jme3.math.ColorRGBA(color.r, color.g, color.b, color.a);
	}

	public static com.jme3.math.Vector3f twoToThree(com.jme.math.Vector3f vector3f){
		return new Vector3f(vector3f.x, vector3f.y, vector3f.z);
	}

	public static Matrix3f twoToThree(com.jme.math.Matrix3f m){
		return new Matrix3f(m.m00, m.m01, m.m02, m.m10, m.m11, m.m12, m.m20, m.m21, m.m22);
	}

	public static Quaternion twoToThree(com.jme.math.Quaternion q){
		return new Quaternion(q.x, q.y, q.z, q.w);
	}

	public static Texture twoToThree(com.jme.image.Texture texture){
		try{
			if(texture.getImage()==null){
				logger.warning("WTF, null texture coming from jME2?");
				return null;
			}
		}catch(NullPointerException e){
			logger.warning("WTF, null texture coming from jME2?");
			return null;
		}
		Image jme3Image = twoToThree(texture.getImage());
		if(jme3Image==null){
			return null;
		}
		if(texture instanceof com.jme.image.Texture1D){
			System.out.println("1D texture");
			return new Texture2D(jme3Image);
		}
		else if(texture instanceof com.jme.image.Texture2D){
			return new Texture2D(jme3Image);
		}
		else if(texture instanceof com.jme.image.Texture3D){
			System.out.println("1D texture");
			return new Texture2D(jme3Image);
		}
		else{
			System.out.println("Conversion failed");
			return null;
		}
	}

	public static Image twoToThree(com.jme.image.Image image){
		//Image.Format.
		if(isImageFormatSupported(image.getFormat())){
			return new Image(Image.Format.valueOf(image.getFormat().name()), image.getWidth(), image.getHeight(), image.getDepth(), image.getData());
		}
		else{
			logger.warning(image.getFormat().name() + " is not supported in jME3!");
			return null;
		}
	}

	public static boolean isImageFormatSupported(com.jme.image.Image.Format format){
		return !unsupportedImageFormat.contains(format);
	}



	@Override
	public Object load(AssetInfo assetInfo) throws IOException{

		try{
			System.out.println("Setting up Dummy Display System");
			DummySystemProvider dsp = new DummySystemProvider();
			com.jme.system.DisplaySystem.setSystemProvider(dsp);
			if(com.jme.system.DisplaySystem.getDisplaySystem().getRenderer()==null){
				System.out.println("There should be a Renderer");
			}
		}catch (IllegalStateException e){
			System.out.println("System provider already set");
		}

		Object loadedObject = null;	

		// load the jME2 Spatial based on whether its an xml or binary file
		if(assetInfo.getKey().getExtension().equals("xml")){
			loadedObject = XMLImporter.getInstance().load(assetInfo.openStream());
		}
		else if(assetInfo.getKey().getExtension().equals("jme")){
			loadedObject = BinaryImporter.getInstance().load(assetInfo.openStream());
		}

		if(loadedObject instanceof com.jme.scene.Spatial){
			// run the converter
			long start = System.currentTimeMillis();
			com.jme3.scene.Spatial spatial = twoToThree(((com.jme.scene.Spatial)loadedObject), assetInfo.getManager());
			System.out.println("Conversion took "+(System.currentTimeMillis()-start)+"ms");
			return spatial;
		}
		else return null;

	}

}
