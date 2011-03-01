/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.seam.forge.spec.jpa;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;

import org.jboss.seam.forge.parser.java.JavaClass;
import org.jboss.seam.forge.parser.java.JavaSource;
import org.jboss.seam.forge.project.dependencies.Dependency;
import org.jboss.seam.forge.project.dependencies.DependencyBuilder;
import org.jboss.seam.forge.project.facets.BaseFacet;
import org.jboss.seam.forge.project.facets.DependencyFacet;
import org.jboss.seam.forge.project.facets.JavaSourceFacet;
import org.jboss.seam.forge.project.facets.ResourceFacet;
import org.jboss.seam.forge.project.packaging.PackagingType;
import org.jboss.seam.forge.resources.DirectoryResource;
import org.jboss.seam.forge.resources.FileResource;
import org.jboss.seam.forge.resources.Resource;
import org.jboss.seam.forge.resources.java.JavaResource;
import org.jboss.seam.forge.shell.plugins.Alias;
import org.jboss.seam.forge.shell.plugins.RequiresFacet;
import org.jboss.seam.forge.shell.plugins.RequiresPackagingType;
import org.jboss.shrinkwrap.descriptor.api.DescriptorImporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.spec.jpa.persistence.PersistenceDescriptor;

/**
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
@Alias("forge.spec.jpa")
@RequiresFacet({ JavaSourceFacet.class, ResourceFacet.class, DependencyFacet.class })
@RequiresPackagingType({ PackagingType.JAR, PackagingType.WAR })
public class PersistenceFacet extends BaseFacet
{
   private static final Dependency dep =
            DependencyBuilder.create("org.jboss.spec:jboss-javaee-6.0:1.0.0.CR1:provided:basic");

   public String getEntityPackage()
   {
      JavaSourceFacet sourceFacet = project.getFacet(JavaSourceFacet.class);
      return sourceFacet.getBasePackage() + ".domain";
   }

   public DirectoryResource getEntityPackageFile()
   {
      JavaSourceFacet sourceFacet = project.getFacet(JavaSourceFacet.class);
      return sourceFacet.getBasePackageResource().getChildDirectory("domain");
   }

   public PersistenceDescriptor getConfig()
   {
      DescriptorImporter<PersistenceDescriptor> importer = Descriptors.importAs(PersistenceDescriptor.class);
      PersistenceDescriptor descriptor = importer.from(getConfigFile().getResourceInputStream());
      return descriptor;
   }

   public void saveConfig(final PersistenceDescriptor descriptor)
   {
      String output = descriptor.exportAsString();
      getConfigFile().setContents(output);
   }

   private FileResource<?> getConfigFile()
   {
      ResourceFacet resources = project.getFacet(ResourceFacet.class);
      return (FileResource<?>) resources.getResourceFolder().getChild("META-INF" + File.separator + "persistence.xml");
   }

   public List<JavaClass> getAllEntities()
   {
      DirectoryResource packageFile = getEntityPackageFile();
      return findEntitiesInFolder(packageFile);
   }

   private List<JavaClass> findEntitiesInFolder(final DirectoryResource packageFile)
   {
      List<JavaClass> result = new ArrayList<JavaClass>();
      if (packageFile.exists())
      {
         for (Resource<?> source : packageFile.listResources())
         {
            if (source instanceof JavaResource)
            {
               try
               {
                  JavaSource<?> javaClass = ((JavaResource) source).getJavaSource();
                  if (javaClass.hasAnnotation(Entity.class) && javaClass.isClass())
                  {
                     result.add((JavaClass) javaClass);
                  }
               }
               catch (FileNotFoundException e)
               {
                  throw new IllegalStateException(e);
               }
            }
         }

         for (Resource<?> source : packageFile.listResources())
         {
            if (source instanceof DirectoryResource)
            {
               List<JavaClass> subResults = findEntitiesInFolder((DirectoryResource) source);
               result.addAll(subResults);
            }
         }
      }
      return result;
   }

   @Override
   public boolean install()
   {
      if (!isInstalled())
      {
         DependencyFacet deps = project.getFacet(DependencyFacet.class);
         if (!deps.hasDependency(dep))
         {
            deps.addDependency(dep);
         }

         DirectoryResource entityRoot = getEntityPackageFile();
         if (!entityRoot.exists())
         {
            entityRoot.mkdirs();
         }

         FileResource<?> descriptor = getConfigFile();
         if (!descriptor.exists())
         {

            PersistenceDescriptor descriptorContents = Descriptors.create(PersistenceDescriptor.class)
                     .version("2.0");
            descriptor.setContents(descriptorContents.exportAsString());

         }
      }
      project.registerFacet(this);
      return true;
   }

   @Override
   public boolean isInstalled()
   {
      DependencyFacet deps = project.getFacet(DependencyFacet.class);
      boolean hasDependency = deps.hasDependency(dep);
      return hasDependency && getEntityPackageFile().exists() && getConfigFile().exists();
   }
}
