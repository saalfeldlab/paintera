# run first (in bigcat root directory):
# export CLASSPATH=/path/to/bigcat-0.0.1-SNAPSHOT.jar:$(mvn dependency:build-classpath | grep  '^[^[]')

import imglyb

from jnius import autoclass

import time

if __name__ == "__main__":

	BigDataViewerJan = autoclass('bdv.bigcat.BigCatViewerJan')
	Parameters = autoclass('bdv.bigcat.BigCatViewerJan$Parameters')
	BdvWindowClosedCheck = autoclass('net.imglib2.python.BdvWindowClosedCheck')

	# set up arguments
	# use hard coded arguments for now

	rawFile = '/groups/saalfeld/home/hanslovskyp/from_funkej/phil/sample_B.augmented.0.hdf'
	groundTruthFile = rawFile
	predictionFile = '/groups/saalfeld/home/hanslovskyp/from_funkej/phil/sample_B_median_aff_cf_hq_dq_au00.87.hdf'
	offsetArray = [ 560, 424, 424 ][::-1]
	resolutionArray = [ 40, 4, 4 ][::-1]

	p = Parameters() \
	  .setRawFile( rawFile ) \
	  .setGroundTruthFile( groundTruthFile ) \
	  .setPredictionFile( predictionFile ) \
	  .setOffsetArray( offsetArray ) \
	  .setResolutionArray( resolutionArray )

	check = BdvWindowClosedCheck()
	bigCat = BigDataViewerJan.run( p )
	bdv = bigCat.getBigDataViewer()
	bdv.getViewerFrame().addWindowListener( check )
	while check.isOpen():
		time.sleep( 0.1 )

	
	
	
