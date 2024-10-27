To create the nuget package:
Make sure that a custombuild is in the vcxproj:
  <ItemGroup>
    <CustomBuild Include="SalmonNative.nuspec">
      <Message>Building nuget package...</Message>
      <Command>..\packages\NuGet.CommandLine.6.7.0\tools\NuGet.exe pack %(Identity) -outputDir ..\..\..\..\output\nuget\releases</Command>
      <Outputs>..\..\..\..\output\nuget\releases</Outputs>
    </CustomBuild>
  </ItemGroup>

  <PropertyGroup>
    <CustomBuildAfterTargets>ClCompile</CustomBuildAfterTargets>
    <CustomBuildBeforeTargets>Link</CustomBuildBeforeTargets>
  </PropertyGroup>

Otherwise you can create it manually:
..\packages\NuGet.CommandLine.6.7.0\tools\NuGet.exe pack SalmonNative.nuspec -OutputDirectory ..\..\..\..\output\nuget\releases\
